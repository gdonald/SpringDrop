package dev.springdrop.kernel.update;

import dev.springdrop.kernel.maintenance.Maintenance;
import dev.springdrop.kernel.maintenance.MaintenanceSettings;
import dev.springdrop.kernel.module.ModuleInfo;
import dev.springdrop.kernel.module.ModuleManager;
import dev.springdrop.kernel.module.ModuleRegistry;
import dev.springdrop.kernel.tx.TransactionRunner;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Finds the update routines a site has yet to run and runs them. A routine is
 * pending when its module, or core, is installed and its version is above the
 * last version recorded for the module. Routines run core first, then each
 * module in the order modules install in, each in version order, every one in
 * its own transaction and recorded as soon as it succeeds. The site is in
 * maintenance mode while they run, and the run stops at the first routine that
 * fails, leaving it and the rest pending.
 */
@Component
public class UpdateManager {

    /** The name core's own update routines are recorded under. */
    public static final String SYSTEM = "system";

    private static final Table<Record> MODULE_UPDATE = DSL.table(DSL.name("module_update"));

    private static final Field<String> MODULE = DSL.field(DSL.name("module"), SQLDataType.VARCHAR);

    private static final Field<Integer> VERSION = DSL.field(DSL.name("version"), SQLDataType.INTEGER);

    private static final Field<OffsetDateTime> APPLIED_AT = DSL.field(DSL.name("applied_at"),
            SQLDataType.TIMESTAMPWITHTIMEZONE);

    private final List<UpdateRoutine> routines;
    private final ModuleRegistry registry;
    private final ModuleManager modules;
    private final Maintenance maintenance;
    private final TransactionRunner transactions;
    private final DSLContext dsl;
    private final Clock clock;

    public UpdateManager(List<UpdateRoutine> routines, ModuleRegistry registry, ModuleManager modules,
            Maintenance maintenance, TransactionRunner transactions, DSLContext dsl, Clock clock) {
        this.routines = List.copyOf(routines);
        this.registry = registry;
        this.modules = modules;
        this.maintenance = maintenance;
        this.transactions = transactions;
        this.dsl = dsl;
        this.clock = clock;
    }

    /** The outcome of a run: the routines applied, and the one that failed with why, if one did. */
    public record Report(List<UpdateRoutine> applied, Optional<UpdateRoutine> failed, String reason) {
    }

    /** The last version applied for a module, if any was recorded. */
    public Optional<Integer> lastApplied(String module) {
        return Optional.ofNullable(dsl.select(VERSION).from(MODULE_UPDATE).where(MODULE.eq(module)).fetchOne(VERSION));
    }

    /** The routines still to run, in the order they run in. */
    public List<UpdateRoutine> pending() {
        List<String> order = new ArrayList<>(List.of(SYSTEM));
        registry.installOrder().stream().map(ModuleInfo::name).filter(modules::isInstalled).forEach(order::add);
        List<UpdateRoutine> pending = new ArrayList<>();
        for (String module : order) {
            int applied = lastApplied(module).orElse(0);
            routines.stream().filter(routine -> routine.module().equals(module) && routine.version() > applied)
                    .sorted(Comparator.comparingInt(UpdateRoutine::version)).forEach(pending::add);
        }
        return List.copyOf(pending);
    }

    /** Runs every pending routine with the site in maintenance mode, which is put back as it was after. */
    public Report runPending() {
        MaintenanceSettings before = maintenance.settings();
        maintenance.save(new MaintenanceSettings(true, before.message()));
        List<UpdateRoutine> applied = new ArrayList<>();
        try {
            for (UpdateRoutine routine : pending()) {
                try {
                    transactions.run(() -> {
                        routine.run();
                        record(routine.module(), routine.version());
                    });
                } catch (RuntimeException failure) {
                    return new Report(List.copyOf(applied), Optional.of(routine), String.valueOf(failure.getMessage()));
                }
                applied.add(routine);
            }
            return new Report(List.copyOf(applied), Optional.empty(), "");
        } finally {
            maintenance.save(before);
        }
    }

    /**
     * Records every routine there is as applied, as for a site installed with
     * them already, which has nothing to update.
     */
    public void markCurrent() {
        routines.stream().map(UpdateRoutine::module).distinct().forEach(this::markCurrent);
    }

    @EventListener
    void moduleInstalled(ModuleInstalledEvent event) {
        markCurrent(event.module());
    }

    private void markCurrent(String module) {
        routines.stream().filter(routine -> routine.module().equals(module)).mapToInt(UpdateRoutine::version).max()
                .ifPresent(version -> record(module, version));
    }

    private void record(String module, int version) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        dsl.insertInto(MODULE_UPDATE).set(MODULE, module).set(VERSION, version).set(APPLIED_AT, now)
                .onConflict(MODULE).doUpdate().set(VERSION, version).set(APPLIED_AT, now).execute();
    }
}
