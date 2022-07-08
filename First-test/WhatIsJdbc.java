
import net.sourceforge.jtds.jdbc.Driver;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

public class WhatIsJdbc
{
	public static void main(String[] args)
	{
		String server = "***";
		String database = "***";
		String instance = "***";

		String url = "jdbc:jtds:sqlserver://" + server + "/" + database +
			";sendStringParametersAsUnicode=false;instance=" + instance;

		String user = "***";
		String password = "***";
		String domain = "***";

		Properties props = new Properties();
		props.setProperty("user", user);
		props.setProperty("password", password);
		props.setProperty("domain", domain);
		// props.setProperty("ssl", "true");

		try
		{
			DriverManager.registerDriver(new Driver());

			try (Connection conn = DriverManager.getConnection(url, props);
				Statement stmt = conn.createStatement())
			{
				try
				{
					ResultSet rs = stmt.executeQuery(
						"select * from information_schema.tables");

					ResultSetMetaData rsmd = rs.getMetaData();
					int numberOfColumns = rsmd.getColumnCount();
					{
						for (int i = 1; i <= numberOfColumns; ++i)
							System.out.print(rsmd.getColumnName(i) + "\t");
						System.out.println();
					}

					while (rs.next())
					{
						for (int i = 1; i <= numberOfColumns; ++i)
							System.out.print(rs.getString(i) + "\t");
						System.out.println();
						// String name = rs.getString(1);
						// System.out.println(name);
					}
				}
				catch (SQLException e)
				{
					// throw new Error("Problem", e);
					e.printStackTrace();
				}
			}
			catch (SQLException e)
			{
				// throw new Error("Problem", e);
				e.printStackTrace();
			}
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
	}
}
