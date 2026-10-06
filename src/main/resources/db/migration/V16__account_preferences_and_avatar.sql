ALTER TABLE users
 ADD COLUMN avatar_media_id uuid REFERENCES media_objects(id),
 ADD COLUMN avatar_color integer NOT NULL DEFAULT 0 CHECK (avatar_color BETWEEN 0 AND 9),
 ADD COLUMN language_tag varchar(10) NOT NULL DEFAULT 'en' CHECK (language_tag IN ('en','zh-CN')),
 ADD COLUMN theme varchar(10) NOT NULL DEFAULT 'light' CHECK (theme IN ('light','dark'));
CREATE INDEX users_avatar_media_idx ON users(avatar_media_id) WHERE avatar_media_id IS NOT NULL;
