package jbuild.extension.runner;

import jbuild.api.JBuildException;

import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static jbuild.api.JBuildException.ErrorCause.ACTION_ERROR;

enum WorkingDirLock {
    /**
     * The real instance of this class which will actually change the current directory.
     */
    INSTANCE(false),
    /**
     * Test instance of this class. Does everything except actually change the current directory.
     */
    TESTER(true);

    // all mutable state in this class, except the semaphore, is protected by this lock!
    private final Lock stateLock = new ReentrantLock(true);

    private final boolean changeDirectory;
    private final Semaphore semaphore = new Semaphore(1, true);
    private String previousWorkingDir = "";
    private String currentWorkingDir = "";
    private int enterCount = 0;

    WorkingDirLock(boolean changeDirectory) {
        this.changeDirectory = changeDirectory;
    }

    void enter(String workingDir) {
        if (workingDir == null) {
            workingDir = "";
        }

        while (true) {
            var didEnter = tryEnter(workingDir);

            if (didEnter) return;

            // wait until we can acquire the semaphore so that we can switch to a new directory
            acquireSemaphoreSafely();
        }
    }

    private boolean tryEnter(String workingDir) {
        stateLock.lock();

        try {
            if (currentWorkingDir.isEmpty()) {
                currentWorkingDir = workingDir;
            }

            if (workingDir.equals(currentWorkingDir)) {
                if (enterCount == 0) {
                    // acquire the semaphore when we actually enter a directory for the first time
                    acquireSemaphoreSafely();
                    if (changeDirectory) {
                        doEnter(workingDir);
                    }
                }
                enterCount++;
                return true;
            }
        } finally {
            stateLock.unlock();
        }

        return false;
    }

    /**
     * Signal that the caller is leaving a directory.
     * <p>
     * This does not mean the working directory is restored to a previous state. That only happens when all callers
     * that had successfully entered the same workingDir have left.
     *
     * @param workingDir to leave,
     *                   should match the current workingDir entered with {@link WorkingDirLock#enter(String)}.
     * @return true if the workingDir was actually entered, false otherwise, which indicates an illegal state
     */
    boolean leave(String workingDir) {
        stateLock.lock();
        try {
            if (currentWorkingDir.equals(workingDir)) {
                enterCount--;
                currentWorkingDir = "";
                if (enterCount == 0) {
                    if (changeDirectory) {
                        doLeave();
                    }
                    semaphore.release();
                }
                return true;
            } else {
                return false;
            }
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Change the process's workingDir.
     * <p>
     * This method assumes the stateLock is held by the caller!
     *
     * @param workingDir to enter
     */
    private void doEnter(String workingDir) {
        previousWorkingDir = System.getProperty("user.dir");
        System.setProperty("user.dir", workingDir);
    }

    /**
     * Change the process's workingDir back to its previous value.
     * <p>
     * This method assumes the stateLock is held by the caller!
     */
    private void doLeave() {
        System.setProperty("user.dir", previousWorkingDir);
        previousWorkingDir = "";
    }

    private void acquireSemaphoreSafely() {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            throw new JBuildException("Interrupted while waiting to change workingDir", ACTION_ERROR);
        }
    }

}
