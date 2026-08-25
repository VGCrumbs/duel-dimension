package de.cas_ual_ty.dueldimension.ocg;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Opens the SQLite card databases.
 * <p>
 * {@link DriverManager} finds drivers with a {@link java.util.ServiceLoader},
 * which does not see the sqlite-jdbc jar from inside Minecraft's module
 * classloader — the driver is present but silently unregistered, and every
 * connection fails with "No suitable driver". Registering it by hand once, on
 * first use, makes the same code work in tests and in game.
 */
public final class Sqlite
{
    private static volatile boolean registered;

    private Sqlite()
    {
    }

    public static Connection open(Path database) throws SQLException
    {
        register();
        return DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
    }

    private static synchronized void register() throws SQLException
    {
        if(registered)
        {
            return;
        }
        try
        {
            DriverManager.registerDriver(new org.sqlite.JDBC());
            registered = true;
        }
        catch(Throwable e)
        {
            throw new SQLException("SQLite driver unavailable", e);
        }
    }
}
