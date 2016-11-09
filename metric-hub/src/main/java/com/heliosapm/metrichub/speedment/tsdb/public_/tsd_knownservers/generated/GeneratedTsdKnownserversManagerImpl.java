package com.heliosapm.metrichub.speedment.tsdb.public_.tsd_knownservers.generated;

import com.heliosapm.metrichub.speedment.tsdb.public_.tsd_knownservers.TsdKnownservers;
import com.speedment.runtime.config.identifier.TableIdentifier;
import com.speedment.runtime.core.manager.AbstractManager;
import com.speedment.runtime.field.Field;
import java.util.stream.Stream;
import javax.annotation.Generated;

/**
 * The generated base implementation for the manager of every {@link
 * com.heliosapm.metrichub.speedment.tsdb.public_.tsd_knownservers.TsdKnownservers}
 * entity.
 * <p>
 * This file has been automatically generated by Speedment. Any changes made to
 * it will be overwritten.
 * 
 * @author Speedment
 */
@Generated("Speedment")
public abstract class GeneratedTsdKnownserversManagerImpl extends AbstractManager<TsdKnownservers> implements GeneratedTsdKnownserversManager {
    
    private final TableIdentifier<TsdKnownservers> tableIdentifier;
    
    protected GeneratedTsdKnownserversManagerImpl() {
        this.tableIdentifier = TableIdentifier.of("tsdb", "public", "tsd_knownservers");
    }
    
    @Override
    public TableIdentifier<TsdKnownservers> getTableIdentifier() {
        return tableIdentifier;
    }
    
    @Override
    public Stream<Field<TsdKnownservers>> fields() {
        return Stream.of(
            TsdKnownservers.HOST,
            TsdKnownservers.PORT,
            TsdKnownservers.UP,
            TsdKnownservers.URI,
            TsdKnownservers.CREATED,
            TsdKnownservers.LAST_UPDATE
        );
    }
    
    @Override
    public Stream<Field<TsdKnownservers>> primaryKeyFields() {
        return Stream.of(
            TsdKnownservers.HOST,
            TsdKnownservers.PORT
        );
    }
}