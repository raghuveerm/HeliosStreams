package com.heliosapm.metrichub.speedment.tsdb.public_.tsd_lastsync.generated;

import com.heliosapm.metrichub.speedment.tsdb.public_.tsd_lastsync.TsdLastsync;
import com.heliosapm.metrichub.speedment.tsdb.public_.tsd_lastsync.TsdLastsyncImpl;
import com.speedment.common.injector.annotation.ExecuteBefore;
import com.speedment.runtime.config.identifier.TableIdentifier;
import com.speedment.runtime.core.component.sql.SqlPersistenceComponent;
import com.speedment.runtime.core.component.sql.SqlStreamSupplierComponent;
import com.speedment.runtime.core.exception.SpeedmentException;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.annotation.Generated;
import static com.speedment.common.injector.State.RESOLVED;

/**
 * The generated Sql Adapter for a {@link
 * com.heliosapm.metrichub.speedment.tsdb.public_.tsd_lastsync.TsdLastsync}
 * entity.
 * <p>
 * This file has been automatically generated by Speedment. Any changes made to
 * it will be overwritten.
 * 
 * @author Speedment
 */
@Generated("Speedment")
public abstract class GeneratedTsdLastsyncSqlAdapter {
    
    private final TableIdentifier<TsdLastsync> tableIdentifier;
    
    protected GeneratedTsdLastsyncSqlAdapter() {
        this.tableIdentifier = TableIdentifier.of("tsdb", "public", "tsd_lastsync");
    }
    
    @ExecuteBefore(RESOLVED)
    void installMethodName(SqlStreamSupplierComponent streamSupplierComponent, SqlPersistenceComponent persistenceComponent) {
        streamSupplierComponent.install(tableIdentifier, this::apply);
        persistenceComponent.install(tableIdentifier);
    }
    
    protected TsdLastsync apply(ResultSet resultSet) throws SpeedmentException{
        final TsdLastsync entity = createEntity();
        try {
            entity.setTableName(resultSet.getString(1));
            entity.setOrdering(resultSet.getShort(2));
            entity.setLastSync(resultSet.getTimestamp(3));
        } catch (final SQLException sqle) {
            throw new SpeedmentException(sqle);
        }
        return entity;
    }
    
    protected TsdLastsyncImpl createEntity() {
        return new TsdLastsyncImpl();
    }
}