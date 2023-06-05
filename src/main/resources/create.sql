create table if not exists QQ_MESSAGE
(
    ID         LONG auto_increment    not null,
    MESSAGE_ID INTEGER                not null,
    BOT_ID     BIGINT                 not null,
    OBJ_ID     INTEGER                not null,
    SENDER     BIGINT                 not null,
    TARGET     BIGINT                 not null,
    TYPE       CHARACTER VARYING(15)  not null,
    JSON_TXT   CHARACTER LARGE OBJECT not null,
    HANDLED    BOOLEAN                not null,
    MSG_TIME   DATETIME               not null,
    constraint "QQ_MESSAGE_pk"
        primary key (ID),
    constraint "QQ_MESSAGE_pk2"
        unique (MESSAGE_ID, BOT_ID, OBJ_ID, TYPE)
);

create unique index if not exists "QQ_MESSAGE_MESSAGE_ID_BOT_ID_OBJ_ID_TYPE_uindex"
    on QQ_MESSAGE (MESSAGE_ID, BOT_ID, OBJ_ID, TYPE);

create index if not exists "QQ_MESSAGE_HANDLED"
    on QQ_MESSAGE (HANDLED);

create table if not exists QQ_TG
(
    ID        LONG auto_increment not null,
    QQ_ID     BIGINT              not null,
    QQ_MSG_ID INTEGER             not null,
    TG_GRP_ID BIGINT              not null,
    TG_MSG_ID BIGINT              not null,
    constraint "QQ_TG_pk"
        primary key (ID)
);

create unique index if not exists "QQ_TG_QQ_ID_uindex"
    on QQ_TG (QQ_ID);

create unique index if not exists "QQ_TG_TG_uindex"
    on QQ_TG (TG_GRP_ID, TG_MSG_ID);
