package de.cas_ual_ty.dueldimension.clientutil;

import de.cas_ual_ty.dueldimension.task.Task;
import de.cas_ual_ty.dueldimension.task.TaskPriority;

public class ClientTask extends Task
{
    public ClientTask(String name, int priority, Runnable task)
    {
        super(name, priority, task);
    }
    
    public ClientTask(TaskPriority priority, Runnable task)
    {
        super(priority, task);
    }
    
    @Override
    public boolean isCanceled()
    {
        return !ClientProxy.getMinecraft().isRunning() || super.isCanceled();
    }
}
