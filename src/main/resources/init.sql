CREATE TABLE IF NOT EXISTS file_cache
(
    id
    varchar
(
    255
) not null
    primary key,
    file_id varchar
(
    255
),
    file_type varchar
(
    255
),
    version integer default 0 not null
    );

CREATE TABLE IF NOT EXISTS friend_config
(
    qq_id
    bigint
    not
    null
    bot_id
    bigint,
    name
    TEXT,
    version
    integer
    default
    0
    not
    null,
    id
    bigint
    not
    null
    primary
    key,
    telegram_group_id
    integer
    not
    null
);
create unique index FRIEND_CONFIG_UIDX
    on friend_config (qq_id asc, telegram_group_id asc);

CREATE TABLE IF NOT EXISTS group_config
(
    id
    bigint
    not
    null
    primary
    key,
    discord_channel_id
    bigint,
    name
    varchar
(
    255
),
    qq_group_id bigint,
    status varchar
(
    255
) not null,
    telegram_group_id bigint,
    version integer default 0 not null
    );
create unique index GROUP_CONFIG_UIDX
    on group_config (telegram_group_id asc);

CREATE TABLE IF NOT EXISTS qq_discord
(
    id
    bigint
    not
    null
    primary
    key,
    discord_channel_id
    bigint,
    discord_msg_id
    bigint,
    qq_grp_id
    bigint,
    qq_msg_id
    integer,
    version
    integer
    default
    0
    not
    null
);

CREATE TABLE IF NOT EXISTS qq_message
(
    id
    bigint
    not
    null
    primary
    key,
    bot_id
    bigint,
    from_id
    bigint,
    handled
    boolean,
    json_txt
    clob,
    message_id
    integer,
    status
    varchar
(
    255
) default NORMAL,
    target_id bigint,
    time timestamp,
    type varchar
(
    255
),
    version integer default 0 not null,
    check
(
    status
    in
(
    'NORMAL',
    'DELETED',
    'REVISED'
)),
    check
(
    type
    in
(
    'GROUP',
    'FRIEND',
    'TEMP',
    'STRANGER'
))
    );

create unique index QQ_MESSAGE_UIDX
    on qq_message (bot_id asc, target_id asc, message_id desc);

CREATE TABLE IF NOT EXISTS qq_tg
(
    id
    bigint
    not
    null
    primary
    key,
    qq_id
    bigint,
    qq_msg_id
    integer,
    tg_grp_id
    bigint,
    tg_msg_id
    bigint,
    version
    integer
    default
    0
    not
    null
);

CREATE TABLE IF NOT EXISTS qq_discord
(
    id
    bigint
    not
    null
    primary
    key,
    qq_msg_id
    bigint,
    qq_group_id
    bigint,
    guild_id
    bigint,
    channel_id
    bigint,
    message_id
    bigint,
    version
    integer
    default
    0
    not
    null
);

CREATE TABLE IF NOT EXISTS user_config
(
    id
    bigint
    not
    null
    primary
    key,
    binding_name
    varchar
(
    255
),
    qq bigint,
    status varchar
(
    255
) not null,
    tg bigint,
    version integer default 0 not null
    );

