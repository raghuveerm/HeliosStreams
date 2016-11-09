package com.heliosapm.metrichub.speedment.tsdb.public_.tsd_tagv.generated;

import com.heliosapm.metrichub.speedment.tsdb.public_.tsd_tagv.TsdTagv;
import com.speedment.runtime.config.identifier.TableIdentifier;
import com.speedment.runtime.core.manager.AbstractManager;
import com.speedment.runtime.field.Field;
import java.util.stream.Stream;
import javax.annotation.Generated;

/**
 * The generated base implementation for the manager of every {@link
 * com.heliosapm.metrichub.speedment.tsdb.public_.tsd_tagv.TsdTagv} entity.
 * <p>
 * This file has been automatically generated by Speedment. Any changes made to
 * it will be overwritten.
 * 
 * @author Speedment
 */
@Generated("Speedment")
public abstract class GeneratedTsdTagvManagerImpl extends AbstractManager<TsdTagv> implements GeneratedTsdTagvManager {
    
    private final TableIdentifier<TsdTagv> tableIdentifier;
    
    protected GeneratedTsdTagvManagerImpl() {
        this.tableIdentifier = TableIdentifier.of("tsdb", "public", "tsd_tagv");
    }
    
    @Override
    public TableIdentifier<TsdTagv> getTableIdentifier() {
        return tableIdentifier;
    }
    
    @Override
    public Stream<Field<TsdTagv>> fields() {
        return Stream.of(
            TsdTagv.XUID,
            TsdTagv.VERSION,
            TsdTagv.NAME,
            TsdTagv.CREATED,
            TsdTagv.LAST_UPDATE,
            TsdTagv.DESCRIPTION,
            TsdTagv.DISPLAY_NAME,
            TsdTagv.NOTES,
            TsdTagv.CUSTOM
        );
    }
    
    @Override
    public Stream<Field<TsdTagv>> primaryKeyFields() {
        return Stream.of(
            TsdTagv.XUID
        );
    }
}