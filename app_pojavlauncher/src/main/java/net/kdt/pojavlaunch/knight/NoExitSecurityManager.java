package net.kdt.pojavlaunch.knight;

import java.security.Permission;

public class NoExitSecurityManager extends SecurityManager {
    @Override
    public void checkPermission(Permission perm) {
        // Allow everything
    }

    @Override
    public void checkPermission(Permission perm, Object context) {
        // Allow everything
    }

    @Override
    public void checkExit(int status) {
        throw new SecurityException("System.exit() intercepted!");
    }
}