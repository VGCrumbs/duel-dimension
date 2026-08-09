package de.cas_ual_ty.dueldimension.task;

public enum TaskPriority
{
    // Below IMG_DOWNLOAD deliberately: pollTask takes the highest priority
    // it can find, so a card somebody is looking at is always served before
    // the background walk over the whole database.
    IMG_PRELOAD("img preload", 1), IMG_DOWNLOAD("img download", 2), IMG_ADJUSTMENT("img adjustment", 2), BINDER_SAVE("binder save", 3), BINDER_LOAD("binder load", 3);
    
    public final String name;
    public final int priority;
    
    TaskPriority(String name, int priority)
    {
        this.name = name;
        this.priority = priority;
    }
}
