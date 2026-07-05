-- V12: Persist chat conversation history (was in-memory).
-- Moi message = 1 dong; thu tu hoi thoai = id tu tang (khoi quan ly seq).

CREATE TABLE IF NOT EXISTS chat_messages (
    id              BIGSERIAL    PRIMARY KEY,
    conversation_id VARCHAR(200) NOT NULL,
    role            VARCHAR(20)  NOT NULL,   -- user | assistant | system
    content         TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- load(conversationId): WHERE conversation_id = ? ORDER BY id
CREATE INDEX IF NOT EXISTS idx_chat_messages_conv ON chat_messages(conversation_id, id);
