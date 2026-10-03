package dev.springdrop.kernel.file;

import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Which things use each managed file. A file put to use becomes permanent, and
 * a file nothing uses any more goes back to being temporary, which cron removes
 * once it is old enough.
 */
@Component
public class FileUsageService {

    private static final Table<?> USAGE = DSL.table(DSL.name("file_usage"));
    private static final Field<Long> FID = DSL.field(DSL.name("fid"), SQLDataType.BIGINT);
    private static final Field<String> MODULE = DSL.field(DSL.name("module"), SQLDataType.VARCHAR);
    private static final Field<String> TYPE = DSL.field(DSL.name("type"), SQLDataType.VARCHAR);
    private static final Field<String> ID = DSL.field(DSL.name("id"), SQLDataType.VARCHAR);
    private static final Field<Integer> COUNT = DSL.field(DSL.name("count"), SQLDataType.INTEGER);
    private static final Field<Integer> STORED_COUNT = DSL.field(DSL.name("file_usage", "count"), SQLDataType.INTEGER);

    private final DSLContext dsl;
    private final FileService files;

    public FileUsageService(DSLContext dsl, FileService files) {
        this.dsl = dsl;
        this.files = files;
    }

    /** Records one more use of a file, which makes it permanent. */
    @Transactional
    public void add(long fileId, String module, String type, Object id) {
        dsl.insertInto(USAGE).columns(FID, MODULE, TYPE, ID, COUNT)
                .values(fileId, module, type, String.valueOf(id), 1)
                .onConflict(FID, MODULE, TYPE, ID).doUpdate().set(COUNT, STORED_COUNT.plus(1))
                .execute();
        files.setPermanent(fileId, true);
    }

    /**
     * Records one use fewer, or every use by the thing when asked. A file nothing
     * uses afterwards goes back to being temporary.
     */
    @Transactional
    public void remove(long fileId, String module, String type, Object id, boolean everyUse) {
        var where = FID.eq(fileId).and(MODULE.eq(module)).and(TYPE.eq(type)).and(ID.eq(String.valueOf(id)));
        if (everyUse) {
            dsl.deleteFrom(USAGE).where(where).execute();
        } else {
            dsl.update(USAGE).set(COUNT, COUNT.minus(1)).where(where).execute();
            dsl.deleteFrom(USAGE).where(where.and(COUNT.le(0))).execute();
        }
        if (total(fileId) == 0) {
            files.setPermanent(fileId, false);
        }
    }

    /** How many times anything uses the file. */
    public int total(long fileId) {
        Integer total = dsl.select(DSL.sum(COUNT).cast(SQLDataType.INTEGER)).from(USAGE).where(FID.eq(fileId))
                .fetchOne(0, Integer.class);
        return (total == null) ? 0 : total;
    }

    public List<FileUsage> usages(long fileId) {
        return dsl.select(FID, MODULE, TYPE, ID, COUNT).from(USAGE).where(FID.eq(fileId))
                .orderBy(MODULE, TYPE, ID)
                .fetch(row -> new FileUsage(row.value1(), row.value2(), row.value3(), row.value4(), row.value5()));
    }

    /** The files one thing uses through one module. */
    public List<Long> filesUsedBy(String module, String type, Object id) {
        return dsl.selectDistinct(FID).from(USAGE).where(MODULE.eq(module)).and(TYPE.eq(type))
                .and(ID.eq(String.valueOf(id)))
                .orderBy(FID).fetch(FID);
    }
}
