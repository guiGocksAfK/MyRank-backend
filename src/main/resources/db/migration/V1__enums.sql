-- =========================================================
-- MyRank — Tipos ENUM (usados por todas as tabelas abaixo)
-- Editado in-place; o banco é dropado a cada mudança de schema.
-- =========================================================

CREATE TYPE auth_provider_type       AS ENUM ('LOCAL', 'GOOGLE', 'DISCORD');
CREATE TYPE plan_type                AS ENUM ('FREE', 'PRO');
CREATE TYPE feed_event_type          AS ENUM ('RATED', 'ADDED', 'BADGE', 'TAKE');
CREATE TYPE reaction_kind            AS ENUM ('UP', 'AGREE', 'DISAGREE');
CREATE TYPE notification_type        AS ENUM ('REACTION', 'FOLLOW', 'TAKE', 'GROUP_ADDED', 'GROUP_APPROVED', 'FOLLOW_REQUEST', 'FOLLOW_ACCEPTED');
CREATE TYPE conversation_type        AS ENUM ('DIRECT', 'GROUP');
CREATE TYPE conversation_member_role AS ENUM ('OWNER', 'ADMIN', 'MOD', 'MEMBER');
CREATE TYPE conversation_access      AS ENUM ('OPEN', 'REQUEST', 'CLOSED');
CREATE TYPE message_kind             AS ENUM ('USER', 'SYSTEM');
