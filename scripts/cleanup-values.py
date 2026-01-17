#!/usr/bin/env python3
"""
Cleanup script for Android string resources.

This script:
1. Cleans up unused string keys from all strings.xml files in values* folders
2. Reports missing keys in each localized strings.xml compared to the base values/strings.xml
"""

import os
import re
import sys
from pathlib import Path
from xml.etree import ElementTree as ET
from typing import Set, Dict, List, Tuple


def get_project_root() -> Path:
    """Get the project root directory (parent of scripts folder)."""
    return Path(__file__).parent.parent


def get_base_strings_path() -> Path:
    """Get the path to the base strings.xml file."""
    return get_project_root() / "app_pojavlauncher" / "src" / "main" / "res" / "values" / "strings.xml"


def get_values_folders() -> List[Path]:
    """Get all values* folders containing strings.xml files."""
    res_path = get_project_root() / "app_pojavlauncher" / "src" / "main" / "res"
    folders = []
    for folder in res_path.iterdir():
        if folder.is_dir() and folder.name.startswith("values"):
            strings_file = folder / "strings.xml"
            if strings_file.exists():
                folders.append(folder)
    return sorted(folders, key=lambda x: x.name)


def parse_string_keys(strings_file: Path) -> Dict[str, Tuple[str, bool]]:
    """
    Parse a strings.xml file and return a dict of {key: (value, is_translatable)}.
    
    The is_translatable flag is False if the string has translatable="false".
    """
    keys = {}
    try:
        tree = ET.parse(strings_file)
        root = tree.getroot()
        for string_elem in root.findall(".//string"):
            name = string_elem.get("name")
            if name:
                translatable = string_elem.get("translatable", "true").lower() != "false"
                # Get the text content (including CDATA)
                if string_elem.text:
                    value = string_elem.text
                else:
                    value = ET.tostring(string_elem, encoding="unicode", method="text")
                keys[name] = (value, translatable)
    except ET.ParseError as e:
        print(f"  Warning: Failed to parse {strings_file}: {e}")
    return keys


def find_used_string_keys(project_root: Path) -> Set[str]:
    """
    Find all string keys used in the project.
    
    Searches for:
    - R.string.xxx in Java/Kotlin files
    - @string/xxx in XML files
    """
    used_keys = set()
    
    # Patterns to find string references
    java_pattern = re.compile(r'R\.string\.(\w+)')
    xml_pattern = re.compile(r'@string/(\w+)')
    
    # Search directories
    search_dirs = [
        project_root / "app_pojavlauncher" / "src" / "main" / "java",
        project_root / "app_pojavlauncher" / "src" / "main" / "res",
    ]
    
    # File extensions to search
    extensions = {'.java', '.kt', '.xml'}
    
    for search_dir in search_dirs:
        if not search_dir.exists():
            continue
        for file_path in search_dir.rglob("*"):
            if file_path.suffix not in extensions:
                continue
            # Skip strings.xml files themselves
            if file_path.name == "strings.xml":
                continue
            try:
                content = file_path.read_text(encoding='utf-8', errors='ignore')
                # Find Java/Kotlin references
                used_keys.update(java_pattern.findall(content))
                # Find XML references
                used_keys.update(xml_pattern.findall(content))
            except Exception as e:
                print(f"  Warning: Failed to read {file_path}: {e}")
    
    return used_keys


def remove_unused_keys_from_file(strings_file: Path, used_keys: Set[str], base_keys: Dict[str, Tuple[str, bool]]) -> Tuple[int, List[str]]:
    """
    Remove unused keys from a strings.xml file.
    Also removes XML comments, empty lines, and sorts keys alphabetically.
    
    Returns a tuple of (number of removed keys, list of removed key names).
    """
    removed_keys = []
    
    try:
        # Read the file content as text
        content = strings_file.read_text(encoding='utf-8')
        
        # Parse to find string names and identify unused keys
        tree = ET.parse(strings_file)
        root = tree.getroot()
        
        for string_elem in root.findall(".//string"):
            name = string_elem.get("name")
            if name and name not in used_keys:
                # Check if it's a non-translatable string in base - these are internal and should be kept
                if name in base_keys and not base_keys[name][1]:
                    continue  # Skip non-translatable strings
                removed_keys.append(name)
        
        # Extract all string elements with their full text representation
        # Use regex to capture complete <string>...</string> elements
        string_pattern = re.compile(
            r'<string\s+name="([^"]+)"([^>]*)>(.*?)</string>',
            re.DOTALL
        )
        
        # Find all string elements
        strings_data = []
        for match in string_pattern.finditer(content):
            name = match.group(1)
            attrs = match.group(2)  # Additional attributes like translatable="false"
            value = match.group(3)
            
            # Skip unused keys
            if name in removed_keys:
                continue
            
            strings_data.append((name, attrs, value))
        
        # Sort by name alphabetically (case-insensitive)
        strings_data.sort(key=lambda x: x[0].lower())
        
        # Get the namespace declaration from the original root
        namespaces = ''
        ns_match = re.search(r'<resources([^>]*)>', content)
        if ns_match:
            namespaces = ns_match.group(1)
        
        # Rebuild the XML content
        new_lines = [f'<?xml version="1.0" encoding="utf-8"?>']
        new_lines.append(f'<resources{namespaces}>')
        
        for name, attrs, value in strings_data:
            # Reconstruct the string element
            new_lines.append(f'    <string name="{name}"{attrs}>{value}</string>')
        
        new_lines.append('</resources>')
        
        new_content = '\n'.join(new_lines) + '\n'
        
        # Write the new content
        strings_file.write_text(new_content, encoding='utf-8')
        
        return len(removed_keys), removed_keys
        
    except Exception as e:
        print(f"  Error processing {strings_file}: {e}")
        return 0, []


def find_missing_keys(base_keys: Dict[str, Tuple[str, bool]], variant_keys: Dict[str, Tuple[str, bool]]) -> List[str]:
    """
    Find keys that are in base but missing from variant.
    
    Only considers translatable keys.
    """
    missing = []
    for key, (value, translatable) in base_keys.items():
        if translatable and key not in variant_keys:
            missing.append(key)
    return sorted(missing)


def main():
    print("=" * 60)
    print("Android String Resources Cleanup Script")
    print("=" * 60)
    print()
    
    project_root = get_project_root()
    print(f"Project root: {project_root}")
    print()
    
    # Step 1: Parse base strings.xml
    base_strings_path = get_base_strings_path()
    if not base_strings_path.exists():
        print(f"Error: Base strings.xml not found at {base_strings_path}")
        sys.exit(1)
    
    print(f"Parsing base strings.xml...")
    base_keys = parse_string_keys(base_strings_path)
    print(f"  Found {len(base_keys)} string keys in base")
    print()
    
    # Step 2: Find all used string keys
    print("Scanning project for string key usage...")
    used_keys = find_used_string_keys(project_root)
    print(f"  Found {len(used_keys)} unique string keys in use")
    print()
    
    # Step 3: Find unused keys
    unused_in_base = set()
    for key, (value, translatable) in base_keys.items():
        if key not in used_keys:
            # Keep non-translatable strings (internal use)
            if translatable:
                unused_in_base.add(key)
    
    print(f"Found {len(unused_in_base)} unused translatable keys in base:")
    if unused_in_base:
        for key in sorted(unused_in_base):
            print(f"    - {key}")
    print()
    
    # Step 4: Process all values folders
    values_folders = get_values_folders()
    print(f"Found {len(values_folders)} values folders with strings.xml")
    print()
    
    print("=" * 60)
    print("CLEANING UP UNUSED KEYS")
    print("=" * 60)
    total_removed = 0
    
    for folder in values_folders:
        strings_file = folder / "strings.xml"
        folder_name = folder.name
        
        removed_count, removed_keys = remove_unused_keys_from_file(strings_file, used_keys, base_keys)
        total_removed += removed_count
        
        # Always print the count for each file
        print(f"{folder_name}: Removed {removed_count} keys")
    
    print(f"\nTotal removed: {total_removed} keys across all files")
    print()
    
    # Step 5: Find commonly missing keys (missing from ALL variant files)
    print("=" * 60)
    print("COMMONLY MISSING KEYS REPORT")
    print("=" * 60)
    print("Keys present in base values/strings.xml but missing from ALL variants:")
    print()
    
    # Get translatable keys from base
    translatable_base_keys = {k for k, (v, t) in base_keys.items() if t}
    
    # Start with all translatable keys as potentially missing from all
    commonly_missing = translatable_base_keys.copy()
    
    variant_count = 0
    for folder in values_folders:
        if folder.name == "values":
            continue  # Skip base folder
        
        variant_count += 1
        strings_file = folder / "strings.xml"
        variant_keys = parse_string_keys(strings_file)
        
        # Remove keys that exist in this variant from the commonly missing set
        commonly_missing -= set(variant_keys.keys())
    
    if commonly_missing:
        print(f"Found {len(commonly_missing)} keys missing from all {variant_count} variant files:")
        for key in sorted(commonly_missing):
            print(f"    - {key}")
    else:
        print(f"No keys are commonly missing from all {variant_count} variants!")
    
    print()
    print("=" * 60)
    print("Done!")
    print("=" * 60)


if __name__ == "__main__":
    main()
