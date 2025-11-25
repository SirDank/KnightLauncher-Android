package com.kdt.mcgui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.core.content.res.ResourcesCompat;

import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import fr.spse.extended_view.ExtendedTextView;

public class mcAccountSpinner extends AppCompatSpinner implements AdapterView.OnItemSelectedListener {
    public mcAccountSpinner(@NonNull Context context) {
        this(context, null);
    }

    public mcAccountSpinner(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private final List<String> mAccountList = new ArrayList<>(2);
    private MinecraftAccount mSelectecAccount = null;

    @SuppressLint("ClickableViewAccessibility")
    private void init() {
        // Set visual properties
        setBackgroundColor(getResources().getColor(R.color.background_status_bar));

        // Set behavior
        reloadAccounts(true, 0);
        setOnItemSelectedListener(this);
    }

    @Override
    public final void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        pickAccount(position);
    }

    @Override
    public final void onNothingSelected(AdapterView<?> parent) {
    }

    public void removeCurrentAccount() {
        removeAccount(getSelectedItemPosition());
    }

    private void removeAccount(int position) {
        File accountFile = new File(Tools.DIR_ACCOUNT_NEW, mAccountList.get(position) + ".json");
        if (accountFile.exists())
            accountFile.delete();
        mAccountList.remove(position);

        reloadAccounts(false, 0);
    }

    /** Allows checking whether we have an online account */
    public boolean isAccountOnline() {
        return true; // Always "online" or valid for KnightLauncher
    }

    public MinecraftAccount getSelectedAccount() {
        return mSelectecAccount;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setNoAccountBehavior() {
        // Set custom behavior when no account are present, to make it act as a button
        if (mAccountList.size() != 0) {
            // Remove any touch listener
            setOnTouchListener(null);
            return;
        }

        // Make the spinner act like a button, since there is no item to really select
        setOnTouchListener((v, event) -> {
            // Do nothing for now
            return true;
        });
    }

    /**
     * Reload the spinner, from memory or from scratch. A default account can be
     * selected
     * 
     * @param fromFiles        Whether we use files as the source of truth
     * @param overridePosition Force the spinner to be at this position, if not 0
     */
    private void reloadAccounts(boolean fromFiles, int overridePosition) {
        if (fromFiles) {
            mAccountList.clear();

            File accountFolder = new File(Tools.DIR_ACCOUNT_NEW);
            if (accountFolder.exists()) {
                for (String fileName : accountFolder.list()) {
                    mAccountList.add(fileName.substring(0, fileName.length() - 5));
                }
            }
        }

        String[] accountArray = mAccountList.toArray(new String[0]);
        AccountAdapter accountAdapter = new AccountAdapter(getContext(), R.layout.item_minecraft_account, accountArray);
        accountAdapter.setDropDownViewResource(R.layout.item_minecraft_account);
        setAdapter(accountAdapter);

        // Pick what's available
        pickAccount(overridePosition);

        // Remove or add the behavior if needed
        setNoAccountBehavior();

    }

    /** Pick the selected account, the one in settings if 0 is passed */
    private void pickAccount(int position) {
        MinecraftAccount selectedAccount;
        if (position != -1 && position < mAccountList.size()) {
            PojavProfile.setCurrentProfile(getContext(), mAccountList.get(position));
            selectedAccount = PojavProfile.getCurrentProfileContent(getContext(), mAccountList.get(position));

            // WORKAROUND
            // Account file corrupted due to previous versions having improper encoding
            if (selectedAccount == null) {
                Context ctx = Objects.requireNonNull(getContext());

                new AlertDialog.Builder(ctx)
                        .setCancelable(false)
                        .setTitle(R.string.account_corrupted)
                        .setMessage(R.string.login_again)
                        .setPositiveButton(R.string.delete_account_and_login, (dialog, which) -> {
                            removeCurrentAccount();
                            pickAccount(0);
                            setSelection(0);
                        })
                        .show();

            }
            setSelection(position);
        } else {
            // Get the current profile, or the first available profile if the wanted one is
            // unavailable
            selectedAccount = PojavProfile.getCurrentProfileContent(getContext(), null);
            int spinnerPosition = selectedAccount == null
                    ? 0
                    : mAccountList.indexOf(selectedAccount.username);
            if (spinnerPosition == -1)
                spinnerPosition = 0;
            setSelection(spinnerPosition, false);
        }

        mSelectecAccount = selectedAccount;
    }

    private class AccountAdapter extends ArrayAdapter<String> {

        public AccountAdapter(@NonNull Context context, int resource, @NonNull String[] objects) {
            super(context, resource, objects);
        }

        @Override
        public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_minecraft_account, parent,
                        false);
            }

            ExtendedTextView textview = convertView.findViewById(R.id.account_item);
            ImageView deleteButton = convertView.findViewById(R.id.delete_account_button);
            textview.setText(super.getItem(position));

            textview.setCompoundDrawables(null, null, null, null);

            deleteButton.setVisibility(View.VISIBLE);
            deleteButton.setOnClickListener(v -> {
                showDeleteDialog(getContext(), position);
            });
            return convertView;
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            View view = getDropDownView(position, convertView, parent);
            view.findViewById(R.id.delete_account_button).setVisibility(View.GONE);
            return view;
        }

        private void showDeleteDialog(Context context, int position) {
            new AlertDialog.Builder(context)
                    .setMessage(R.string.warning_remove_account)
                    .setPositiveButton(android.R.string.cancel, null)
                    .setNeutralButton(R.string.global_delete, (dialog, which) -> {
                        // onDetachedFromWindow(); // This seems wrong to call here?
                        removeAccount(position);
                    })
                    .show();
        }
    }
}