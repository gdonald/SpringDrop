package dev.springdrop.kernel.update;

/**
 * One change a module makes to a site that already has it, to its schema or
 * its data, the way Drupal's {@code hook_update_N} does. A routine is a bean
 * naming its module, {@value UpdateManager#SYSTEM} for core, and a version;
 * the routines of a module run in version order, each once.
 */
public interface UpdateRoutine {

    String module();

    /** The version the routine brings the module to, such as 9001. */
    int version();

    /** What the routine does, shown before it runs. */
    String description();

    void run();
}
