package net.kdt.pojavlaunch.knight;

public interface Progress {
    void postStepProgress(int prg);

    void postPartProgress(int prg);

    void postMaxSteps(int max);

    void postMaxPart(int max);

    void setPartIndeterminate(boolean indeterminate);

    void postLogLine(String line, Throwable th);

    void unlockExit();
}