CREATE TABLE post_votes (user_id uuid REFERENCES users(id), post_id uuid REFERENCES posts(id) ON DELETE CASCADE, value smallint NOT NULL CHECK(value IN(-1,1)), created_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(user_id,post_id));
CREATE INDEX post_votes_target ON post_votes(post_id);
CREATE TABLE comment_votes (user_id uuid REFERENCES users(id), comment_id uuid REFERENCES comments(id) ON DELETE CASCADE, value smallint NOT NULL CHECK(value IN(-1,1)), created_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(user_id,comment_id));
CREATE INDEX comment_votes_target ON comment_votes(comment_id);
CREATE TABLE post_bookmarks (user_id uuid REFERENCES users(id), post_id uuid REFERENCES posts(id) ON DELETE CASCADE, created_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(user_id,post_id));
CREATE INDEX post_bookmarks_target ON post_bookmarks(post_id);
CREATE TABLE user_follows (follower_id uuid REFERENCES users(id), followed_id uuid REFERENCES users(id), created_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(follower_id,followed_id), CHECK(follower_id<>followed_id));
CREATE INDEX user_follows_target ON user_follows(followed_id,follower_id);
-- Virtual tokens only. The wallet row serializes all trades for an account.
CREATE TABLE market_wallets (user_id uuid PRIMARY KEY REFERENCES users(id), cash bigint NOT NULL DEFAULT 1000 CHECK(cash>=0 AND cash<=500000000), credits bigint NOT NULL DEFAULT 1000 CHECK(credits>=1000), reset_date date NOT NULL DEFAULT (now() AT TIME ZONE 'UTC')::date);
CREATE TABLE market_positions (user_id uuid REFERENCES market_wallets(user_id), forum_key varchar(50) NOT NULL, side varchar(5) NOT NULL CHECK(side IN('LONG','SHORT')), units bigint NOT NULL CHECK(units>=0 AND units<=10000), cost bigint NOT NULL CHECK(cost>=0), PRIMARY KEY(user_id,forum_key,side));
CREATE TABLE market_candles (forum_key varchar(50) NOT NULL, bucket timestamptz NOT NULL, open integer NOT NULL CHECK(open>0), high integer NOT NULL, low integer NOT NULL, close integer NOT NULL CHECK(close>0), PRIMARY KEY(forum_key,bucket));
CREATE TABLE market_trades (id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES market_wallets(user_id), request_id uuid NOT NULL, forum_key varchar(50) NOT NULL, action varchar(20) NOT NULL, units integer NOT NULL CHECK(units>0), price integer NOT NULL CHECK(price>0), expected_price integer NOT NULL, cash_before bigint NOT NULL, cash_after bigint NOT NULL, amount bigint NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(user_id,request_id));
CREATE INDEX market_trades_user_page ON market_trades(user_id,created_at DESC,id DESC);
