CREATE TABLE IF NOT EXISTS black_list
(
    id         INTEGER PRIMARY KEY,
    chat_id    BIGINT      NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS pinned_message
(
    id         INTEGER PRIMARY KEY,
    chat_id    BIGINT  NOT NULL,
    message_id INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS user_accepts
(
    id                      INTEGER PRIMARY KEY,
    chat_id                 BIGINT      NOT NULL,
    user_name_tg            TEXT        NOT NULL,
    nomer_soiza             TEXT        NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    face_photo              TEXT        NOT NULL,
    fio                     TEXT        NOT NULL,
    phone_number            TEXT        NOT NULL,
    passport_number         TEXT        NOT NULL,
    role                    TEXT        NOT NULL,
    passport_issue_date     TEXT        NOT NULL,
    passport_issue_date_end TEXT        NOT NULL,
    registration_address    TEXT        NOT NULL,
    passport_photos         TEXT        NOT NULL,
    pavilion_number         TEXT        NOT NULL,
    rental_contract         TEXT        NOT NULL,
    pavilion_photos         TEXT        NOT NULL,
    propiska_photo          TEXT        NOT NULL,
    folder_url              TEXT        NOT NULL
);

CREATE TABLE IF NOT EXISTS user_applications
(
    id                      INTEGER PRIMARY KEY,
    chat_id                 BIGINT      NOT NULL,
    user_name_tg            TEXT        NOT NULL,
    nomer_soiza             TEXT        NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    face_photo              TEXT        NOT NULL,
    fio                     TEXT        NOT NULL,
    phone_number            TEXT        NOT NULL,
    passport_number         TEXT        NOT NULL,
    role                    TEXT        NOT NULL,
    passport_issue_date     TEXT        NOT NULL,
    passport_issue_date_end TEXT        NOT NULL,
    registration_address    TEXT        NOT NULL,
    passport_photos         TEXT        NOT NULL,
    pavilion_number         TEXT        NOT NULL,
    rental_contract         TEXT        NOT NULL,
    pavilion_photos         TEXT        NOT NULL,
    propiska_photo          TEXT        NOT NULL,
    folder_url              TEXT        NOT NULL
);

CREATE TABLE black_list_old AS SELECT * FROM black_list;
CREATE TABLE pinned_message_old AS SELECT * FROM pinned_message;
CREATE TABLE user_accepts_old AS SELECT * FROM user_accepts;
CREATE TABLE user_applications_old AS SELECT * FROM user_applications;

DROP TABLE black_list;
DROP TABLE pinned_message;
DROP TABLE user_accepts;
DROP TABLE user_applications;

CREATE TABLE admin_list
(
    id       BIGSERIAL PRIMARY KEY,
    user_id  BIGINT      NOT NULL,
    created  TIMESTAMPTZ NOT NULL
);

COMMENT ON TABLE admin_list IS 'Список администраторов';
COMMENT ON COLUMN admin_list.id IS 'ИД записи (PK)';
COMMENT ON COLUMN admin_list.user_id IS 'ИД пользователя';
COMMENT ON COLUMN admin_list.created IS 'Дата и время создания записи';

CREATE TABLE black_list
(
    id       BIGSERIAL PRIMARY KEY,
    user_id  BIGINT      NOT NULL,
    created  TIMESTAMPTZ NOT NULL
);

COMMENT ON TABLE black_list IS 'Список забаненных пользователей';
COMMENT ON COLUMN black_list.id IS 'ИД записи (PK)';
COMMENT ON COLUMN black_list.user_id IS 'ИД пользователя';
COMMENT ON COLUMN black_list.created IS 'Дата и время создания записи';

CREATE TABLE pinned_message
(
    id         BIGSERIAL PRIMARY KEY,
    chat_id    BIGINT NOT NULL,
    message_id BIGINT NOT NULL
);

COMMENT ON TABLE pinned_message IS 'Список закрепленных сообщений';
COMMENT ON COLUMN pinned_message.id IS 'ИД записи (PK)';
COMMENT ON COLUMN pinned_message.chat_id IS 'ИД чата';
COMMENT ON COLUMN pinned_message.message_id IS 'ИД сообщения';

CREATE TABLE user_application
(
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT      NOT NULL,
    username           TEXT,
    created            TIMESTAMPTZ NOT NULL,
    fio                TEXT        NOT NULL,
    phone_number       TEXT        NOT NULL,
    role               TEXT        NOT NULL,
    office_number      TEXT,
    status             TEXT        NOT NULL CHECK ( status IN ('NEW', 'APPROVED', 'REJECTED') ),
    decision_timestamp TIMESTAMPTZ,
    decision_user_id   BIGINT
);

COMMENT ON TABLE user_application IS 'Список заявок';
COMMENT ON COLUMN user_application.id IS 'ИД записи (PK)';
COMMENT ON COLUMN user_application.user_id IS 'ИД пользователя';
COMMENT ON COLUMN user_application.username IS 'Имя пользователя';
COMMENT ON COLUMN user_application.created IS 'Дата и время создания записи';
COMMENT ON COLUMN user_application.fio IS 'ФИО';
COMMENT ON COLUMN user_application.phone_number IS 'Номер телефона';
COMMENT ON COLUMN user_application.role IS 'Роль';
COMMENT ON COLUMN user_application.office_number IS 'Номер офиса';
COMMENT ON COLUMN user_application.status IS 'Статус';
COMMENT ON COLUMN user_application.decision_timestamp IS 'Дата и время решения';
COMMENT ON COLUMN user_application.decision_user_id IS 'Пользователь, принявший решение';

INSERT INTO black_list (id, user_id, created)
SELECT id::BIGINT,
       chat_id,
       created_at
FROM black_list_old;

INSERT INTO pinned_message (id, chat_id, message_id)
SELECT id::BIGINT,
       chat_id,
       message_id::BIGINT
FROM pinned_message_old;

INSERT INTO user_application(
    id,
    user_id,
    username,
    created,
    fio,
    phone_number,
    role,
    office_number,
    status,
    decision_timestamp,
    decision_user_id
)
SELECT id,
       chat_id,
       user_name_tg,
       created_at,
       fio,
       regexp_replace(phone_number, '\D+', '', 'g'),
       role,
       pavilion_number,
       'NEW',
       NULL::TIMESTAMPTZ,
       NULL::BIGINT
FROM user_applications_old
UNION ALL
SELECT -id, -- чтобы ИД не пересекались
       chat_id,
       user_name_tg,
       created_at,
       fio,
       regexp_replace(phone_number, '\D+', '', 'g'),
       role,
       pavilion_number,
       'APPROVED',
       NULL,
       NULL
FROM user_accepts_old;

SELECT setval('black_list_id_seq', (SELECT COALESCE(max(id) + 1, 1) FROM black_list_old), FALSE);
SELECT setval('pinned_message_id_seq', (SELECT COALESCE(max(id) + 1, 1) FROM pinned_message_old), FALSE);
SELECT setval('user_application_id_seq', (SELECT COALESCE(max(id) + 1, 1) FROM user_applications_old), FALSE);