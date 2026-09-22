package dev.leafconfig.yaml.testmodel;

import dev.leafconfig.annotation.Comment;
import dev.leafconfig.annotation.ConfigFile;
import dev.leafconfig.annotation.ConfigVersion;
import dev.leafconfig.annotation.FormerlyKnownAs;
import dev.leafconfig.annotation.Range;
import dev.leafconfig.migration.Migrations;
import java.time.Duration;

/**
 * Versioned model shared by the migration tests. History: version 1 stored the database under
 * {@code mysql.ip}/{@code mysql.port} and the greeting under {@code motd}; version 2 moved the
 * database to {@code database.host}/{@code database.port}; version 3 added {@code
 * database.pool-size}. The greeting rename is handled by {@code @FormerlyKnownAs} instead of a
 * migration step.
 */
@ConfigFile("versioned.yml")
@ConfigVersion(3)
@Comment("Versioned fixture configuration")
public final class VersionedConfig {

  @Comment("Enable debug output")
  private boolean debug = false;

  @Comment("Greeting shown on join")
  @FormerlyKnownAs("motd")
  private String greeting = "Welcome!";

  @Comment("Database settings")
  private Database database = new Database();

  private Duration timeout = Duration.ofSeconds(30);

  /** Registers the steps from version 1 to 3. */
  public static void migrations(Migrations migrations) {
    migrations
        .from(1)
        .to(
            2,
            document -> {
              document.rename("mysql.ip", "database.host");
              document.rename("mysql.port", "database.port");
              document.remove("mysql");
            })
        .from(2)
        .to(3, document -> document.setIfMissing("database.pool-size", 10));
  }

  public boolean debug() {
    return debug;
  }

  public String greeting() {
    return greeting;
  }

  public Database database() {
    return database;
  }

  public Duration timeout() {
    return timeout;
  }

  /** Nested section. */
  public static final class Database {

    private String host = "localhost";

    @Range(min = 1, max = 65535)
    private int port = 5432;

    @Comment("Connections kept open")
    @Range(min = 1, max = 100)
    private int poolSize = 5;

    public String host() {
      return host;
    }

    public int port() {
      return port;
    }

    public int poolSize() {
      return poolSize;
    }
  }
}
