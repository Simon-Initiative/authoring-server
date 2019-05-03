package edu.cmu.oli.content.models.persistance;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * A hibernate adapter class for Json type
 *
 * @author Raphael Gachuhi
 */
public class JsonWrapperType implements UserType {

    @Override
    public int[] sqlTypes() {
        return new int[]{Types.CLOB};
    }

    @Override
    public Class<JsonWrapper> returnedClass() {
        return JsonWrapper.class;
    }

    @Override
    public Object deepCopy(final Object value) throws HibernateException {
        if (value == null) {
            return null;
        }
        try {
            return new JsonWrapper(((JsonWrapper) value).getAsString());
//            return new JsonWrapper(AppUtils.gsonBuilder().create().fromJson(AppUtils.gsonBuilder().create().toJson(((JsonWrapper) value).getJsonObject()), JsonElement.class));
        } catch (Exception ex) {
            throw new HibernateException(ex);
        }
    }

    @Override
    public boolean isMutable() {
        return true;
    }

    @Override
    public Serializable disassemble(final Object value) throws HibernateException {
        return (Serializable) this.deepCopy(value);
    }

    @Override
    public Object assemble(final Serializable cached, final Object owner) throws HibernateException {
        return this.deepCopy(cached);
    }

    @Override
    public Object replace(final Object original, final Object target, final Object owner) throws HibernateException {
        return this.deepCopy(original);
    }

    @Override
    public boolean equals(final Object obj1, final Object obj2) throws HibernateException {
        if (obj1 == null) {
            return obj2 == null;
        }
        return obj1.equals(obj2);
    }

    @Override
    public int hashCode(final Object obj) throws HibernateException {
        return obj.hashCode();
    }

    @Override
    public Object nullSafeGet(ResultSet resultSet, String[] strings, SharedSessionContractImplementor sharedSessionContractImplementor, Object o) throws HibernateException, SQLException {
        final String cellContent = resultSet.getString(strings[0]);
        if (cellContent == null) {
            return null;
        }

        try {
            return new JsonWrapper(cellContent);
//            return new JsonWrapper(AppUtils.gsonBuilder().create().fromJson(cellContent, JsonElement.class));
        } catch (Exception ex) {
            throw new HibernateException(ex);
        }
    }

    @Override
    public void nullSafeSet(PreparedStatement ps, Object value, int idx, SharedSessionContractImplementor sharedSessionContractImplementor) throws HibernateException, SQLException {
        if (value == null) {
            ps.setNull(idx, Types.CLOB);
            return;
        }
        try {
//            Gson gson = AppUtils.gsonBuilder().create();
//            String s = gson.toJson(((JsonWrapper) value).getJsonObject());
            String s = ((JsonWrapper) value).getAsString();
            ps.setObject(idx, s, Types.CLOB);
        } catch (Exception ex) {
            throw new HibernateException(ex);
        }
    }
}
