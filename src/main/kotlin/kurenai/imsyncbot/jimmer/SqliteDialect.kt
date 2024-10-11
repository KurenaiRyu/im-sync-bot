package kurenai.imsyncbot.jimmer

import org.babyfish.jimmer.sql.ast.impl.render.AbstractSqlBuilder
import org.babyfish.jimmer.sql.dialect.DefaultDialect
import org.babyfish.jimmer.sql.dialect.Dialect.UpdateContext
import org.babyfish.jimmer.sql.dialect.Dialect.UpsertContext
import org.babyfish.jimmer.sql.dialect.UpdateJoin
import java.math.BigDecimal
import java.sql.Date
import java.sql.Time
import java.sql.Timestamp
import java.time.*
import java.util.*

/**
 * For Sqlite
 */
class SqliteDialect : DefaultDialect() {

    override fun getUpdateJoin(): UpdateJoin {
        return UpdateJoin(true, UpdateJoin.From.UNNECESSARY)
    }

    override fun isDeletedAliasRequired(): Boolean {
        return true
    }

    override fun sqlType(elementType: Class<*>): String? = when (elementType) {
        String::class.java -> {
            "varchar"
        }

        UUID::class.java -> {
            "char(36)"
        }

        Boolean::class.javaPrimitiveType -> {
            "boolean"
        }

        Byte::class.javaPrimitiveType -> {
            "tinyint"
        }

        Short::class.javaPrimitiveType -> {
            "smallint"
        }

        Int::class.javaPrimitiveType -> {
            "int"
        }

        Long::class.javaPrimitiveType -> {
            "bigint"
        }

        Float::class.javaPrimitiveType -> {
            "float"
        }

        Double::class.javaPrimitiveType -> {
            "double"
        }

        BigDecimal::class.java -> {
            "numeric"
        }

        Date::class.java, LocalDate::class.java -> {
            "date"
        }

        Time::class.java, LocalTime::class.java -> {
            "datetime"
        }

        OffsetTime::class.java -> {
            "datetime"
        }

        java.util.Date::class.java, Timestamp::class.java, LocalDateTime::class.java, OffsetDateTime::class.java, ZonedDateTime::class.java -> {
            "datetime"
        }

        else -> {
            null
        }
    }

    override fun update(ctx: UpdateContext) {
        if (!ctx.isUpdatedByKey) {
            super.update(ctx)
            return
        }
        ctx
            .sql("update ")
            .appendTableName()
            .enter(AbstractSqlBuilder.ScopeType.SET)
            .appendAssignments()
            .leave()
            .enter(AbstractSqlBuilder.ScopeType.WHERE)
            .appendPredicates()
            .leave()
            .sql(" returning ")
            .appendId()
    }

    override fun upsert(ctx: UpsertContext) {
        ctx.sql("insert into ")
            .appendTableName()
            .enter(AbstractSqlBuilder.ScopeType.LIST)
            .appendInsertedColumns("")
            .leave()
            .enter(AbstractSqlBuilder.ScopeType.VALUES)
            .enter(AbstractSqlBuilder.ScopeType.LIST)
            .appendInsertingValues()
            .leave()
            .leave()
            .sql(" on conflict")
            .enter(AbstractSqlBuilder.ScopeType.LIST)
            .appendConflictColumns()
            .leave()
        if (ctx.hasUpdatedColumns()) {
            ctx.sql(" do update")
                .enter(AbstractSqlBuilder.ScopeType.SET)
                .appendUpdatingAssignments("excluded.", "")
                .leave()
        } else {
            ctx.sql(" do nothing")
        }
    }
}
