-- Role: artnet

-- DROP ROLE artnet;

CREATE ROLE artnet LOGIN
  ENCRYPTED PASSWORD 'md58491af29e0e30a15cd0242db52c21a44'
  NOSUPERUSER INHERIT CREATEDB NOCREATEROLE;


-- Table: artnet.host

-- DROP TABLE artnet.host;

CREATE TABLE artnet.host
(
  hostid integer NOT NULL DEFAULT nextval('artnet.host_id_seq'::regclass),
  address character varying(100) NOT NULL,
  CONSTRAINT host_pkey PRIMARY KEY (hostid),
  CONSTRAINT host_address_key UNIQUE (address)
)
WITH (
  OIDS=FALSE
);
ALTER TABLE artnet.host OWNER TO artnet;

-- Table: artnet.url

-- DROP TABLE artnet.url;

CREATE TABLE artnet.url
(
  urlid serial NOT NULL,
  hostid integer NOT NULL,
  path character varying(512) NOT NULL,
  last_visited integer,
  visit_count integer,
  update_freq integer,
  entity boolean NOT NULL,
  pattern character varying(100),
  working boolean,
  CONSTRAINT url_pkey PRIMARY KEY (urlid),
  CONSTRAINT url_host FOREIGN KEY (hostid)
      REFERENCES artnet.host (hostid) MATCH SIMPLE
      ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT url_path_key UNIQUE (path)
)
WITH (
  OIDS=FALSE
);
ALTER TABLE artnet.url OWNER TO artnet;

-- Index: artnet.fki_url_host

-- DROP INDEX artnet.fki_url_host;

CREATE INDEX fki_url_host
  ON artnet.url
  USING btree
  (hostid);

-- Table: artnet.url_lock

-- DROP TABLE artnet.url_lock;

CREATE TABLE artnet.url_lock
(
  "owner" integer NOT NULL,
  urlid integer NOT NULL,
  "time" timestamp without time zone,
  CONSTRAINT urlid_pkey PRIMARY KEY (urlid),
  CONSTRAINT url_lock_urlid FOREIGN KEY (urlid)
      REFERENCES artnet.url (urlid) MATCH SIMPLE
      ON UPDATE CASCADE ON DELETE CASCADE
)
WITH (
  OIDS=FALSE
);
ALTER TABLE artnet.url_lock OWNER TO artnet;
