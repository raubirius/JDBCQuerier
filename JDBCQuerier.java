
import java.io.IOException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import knižnica.*;

import net.sourceforge.jtds.jdbc.Driver;

/*
; The configuration file (config.cfg) may contain following properties:

;server.name=localhost
;server.protocol=jdbc:jtds:sqlserver
;server.port=1433
;server.database=***
;server.instance=***

; Parameters are optional. If present, they are appended to the connection
; string in the following form (without the quotes): “;name=value”
;parameter.sendStringParametersAsUnicode=false
;parameter.example=***
;…
; Null parameters (with no value and no equal sign present):
;	parameter.exampleNullParameter
; and parameters with reserved names (name, protocol, and port) are ignored.
; If server.database or server.instance is defined, such property will be
; ignored, too.

; Connection string form sample:
; jdbc:jtds:sqlserver://server.name:port/database;instance=instanceString

;user.name=***
;user.password=***
;user.domain=***

; Properties are optional. If present, they are sent to the connection manager:
;property.ssl=true
;property.example=***
;…
; Empty properties and properties with reserved names (name, password, and
; domain) are ignored.
*/

// (Som example selects.)
// select * from information_schema.tables;

public class JDBCQuerier extends GRobot
{
	private final static String version = "1.0";

	private final static String clear1 = "clear1";
	private final static String clear2 = "clear2";

	private final String defaultStyle =
		// "/* Default style. */\n" +
		"p { margin: 0px; }\n" +
		"p.error { font-weight: bold; color: maroon; }\n" +
		"p.last_query { color: purple; }\n" +
		"p.controls { text-align: center; }\n" +
		"p.summary { color: navy; }\n" +
		"table.frame { background-color: black; }\n" +
		"table.result tr th, table.result tr td " +
			"{ background-color: white; }\n";

	private final String defaultBody = "";
		// "<!-- Default body -->";

	private final StringBuffer head = new StringBuffer(),
		body = new StringBuffer(), table = new StringBuffer();

	private final Properties parameters = new Properties();
	private final Properties connectionProperties = new Properties();
	private final Properties additionalProperties = new Properties();

	private String server = null;
	private String protocol = null;
	private String port = null;
	private String database = null;
	// private String sendStringParametersAsUnicode = null;
	private String instance = null;

	private String connectionURL = null;

	private String user = null;
	private String password = null;
	private String domain = null;

	private Connection connection = null;
	private Statement statement = null;

	private final PoznámkovýBlok blok;

	private String lastQuery = null;
	private int pageRows = 25; // TODO config
	private String[] header = null;
	private final ArrayList<String[]> queryData = new ArrayList<>();


	private JDBCQuerier()
	{
		super(Svet.šírkaZariadenia(), Svet.výškaZariadenia(),
			"JDBC Querier " + version);
		skry();
		// interaktívnyRežim(true); // NOPE

		Svet.začniVstup();
		Svet.neskrývajVstupnýRiadok();
		Svet.aktivujHistóriuVstupnéhoRiadka();
		Svet.uchovajHistóriuVstupnéhoRiadka();

		Svet.položkaPonukyKoniec().text("Exit");
		Svet.položkaPonukyKoniec().mnemonickaSkratka(Kláves.VK_X);
		Svet.premenujPoložkuHlavnejPonuky(0, "Menu", Kláves.VK_M);

		blok = new PoznámkovýBlok();
		blok.roztiahniNaŠírku();
		blok.roztiahniNaVýšku();
		blok.zrušDekor(false);
		blok.neupravuj();

		Svet.pridajKlávesovúSkratku(clear1, Kláves.VK_L);
		Svet.pridajKlávesovúSkratku(clear2, Kláves.VK_K);

		Svet.pridajPoložkuPonuky("Clear", Kláves.VK_C).príkaz(clear1);

		// TODO export
		// TODO fullscreen

		new ObsluhaUdalostí()
		{
			@Override public void čítajKonfiguráciu(Súbor súbor)
				throws java.io.IOException { loadConfig(súbor); }

			@Override public void potvrdenieÚdajov()
			{ doQuery(Svet.prevezmiReťazec()); }

			@Override public void ukončenie() { disconnect(); }

			@Override public void klávesováSkratka() { onCommand(); }

			@Override public void aktiváciaOdkazu()
			{ openURL(ÚdajeUdalostí.poslednýOdkaz()); }
		};

		clear();
		// error(new Error("Test <error>. 'apos'"));
		// error("Test \"error\"&. 'apos'");

		init();
		connect();
	}


	private String replaceHTMLEntities(String text)
	{
		return text.replace("&", "&amp;")
			.replace("<", "&lt;").replace(">", "&gt;")
			.replace("\"", "&quot;")
			// .replace("'", "&apos;") // is not processed by text the pane
			// correctly… (probably a bug)
			;
	}

	private boolean splitFlag = false;

	private void splitHtml()
	{
		if (splitFlag) joinHtml();

		splitFlag = true;
		head.setLength(0);
		body.setLength(0);

		String html = blok.html();
		if (null == html)
		{
			head.append("<style>");
			head.append(defaultStyle);
			head.append("</style>");

			body.append(defaultBody);

			// html = "<html><head><style>" + defaultStyle +
			// 	"</style></head><body>" + defaultBody + "</body></html>";
		}
		else
		{
			int indexOf0 = html.indexOf("<head");
			if (-1 != indexOf0)
			{
				int indexOf1 = html.indexOf(">", indexOf0 + 5);
				if (-1 != indexOf1)
				{
					int indexOf2 = html.indexOf("</head", indexOf1 + 1);
					if (-1 != indexOf2)
					{
						head.append(html.substring(indexOf1 + 1, indexOf2));
						// System.out.println("head: " + head);
					}
				}
			}

			indexOf0 = html.indexOf("<body");
			if (-1 != indexOf0)
			{
				int indexOf1 = html.indexOf(">", indexOf0 + 5);
				if (-1 != indexOf1)
				{
					int indexOf2 = html.indexOf("</body", indexOf1 + 1);
					if (-1 != indexOf2)
					{
						body.append(html.substring(indexOf1 + 1, indexOf2));
						// System.out.println("body: " + body);
					}
				}
			}
		}
	}

	private void joinHtml()
	{
		if (!splitFlag) return;
		splitFlag = false;
		blok.html("<html>\n<head>\n" + head +
			"\n</head>\n<body>\n" + body +
			"\n</body>\n</html>");
	}

	private void error(Throwable t)
	{
		boolean joinFlag = false;
		if (!splitFlag)
		{
			splitHtml();
			joinFlag = true;
		}

		t.printStackTrace();
		body.append("\n<p class=\"error\">");
		body.append(replaceHTMLEntities(t.getMessage()));
		body.append("</p>\n");
		/* … <pre style=\"display:none;\">");
		body.append(replaceHTMLEntities(
			GRobotException.stackTraceToString(t)));
		body.append("</pre>\n");*/

		if (joinFlag) joinHtml();
	}

	private void error(String s)
	{
		boolean joinFlag = false;
		if (!splitFlag)
		{
			splitHtml();
			joinFlag = true;
		}

		System.err.println(s);
		body.append("\n<p class=\"error\">");
		body.append(replaceHTMLEntities(s));
		body.append("</p>\n");

		if (joinFlag) joinHtml();
	}

	private void clear()
	{
		splitFlag = true;
		head.setLength(0);
		body.setLength(0);

		head.append("<style>");
		head.append(defaultStyle);
		head.append("</style>");

		body.append(defaultBody);

		joinHtml();
	}


	private void loadConfig(Súbor súbor) throws java.io.IOException
	{
		parameters.clear();
		connectionProperties.clear();
		additionalProperties.clear();

		server = null;
		protocol = null;
		port = null;
		database = null;
		// sendStringParametersAsUnicode = null;
		instance = null;

		connectionURL = null;

		user = null;
		password = null;
		domain = null;

		Zoznam<String> vlastnosti = súbor.zoznamVlastností();

		súbor.vnorMennýPriestorVlastností("server");
		try
		{
			server = súbor.čítajVlastnosť("name", server);
			protocol = súbor.čítajVlastnosť("protocol", protocol);
			port = súbor.čítajVlastnosť("port", port);
			database = súbor.čítajVlastnosť("database", database);
			// sendStringParametersAsUnicode = súbor.čítajVlastnosť(
			// 	"sendStringParametersAsUnicode", sendStringParametersAsUnicode);
			instance = súbor.čítajVlastnosť("instance", instance);
		}
		finally
		{
			súbor.vynorMennýPriestorVlastností();
		}

		súbor.vnorMennýPriestorVlastností("parameter");
		try
		{
			for (String vlastnosť : vlastnosti)
			{
				if (null == vlastnosť || vlastnosť.isEmpty() ||
					vlastnosť.startsWith(";") ||
					!vlastnosť.startsWith("parameter.")) continue;

				String skrátená = vlastnosť.substring("parameter.".length());
				// System.out.println("parameter: " + skrátená);

				if (skrátená.isEmpty() || skrátená.equals("name") ||
					skrátená.equals("protocol") || skrátená.equals("port")
					// || skrátená.equals("sendStringParametersAsUnicode")
					)
				{
					System.err.println("Warning: Invalid parameter: " +
						skrátená + "\nIt will be ignored.");
					continue;
				}

				if (skrátená.equals("database") && null != database)
				{
					System.err.println("Note: Parameter database is " +
						"ignored because there was already a database " +
						"defined.");
					continue;
				}

				if (skrátená.equals("instance") && null != instance)
				{
					System.err.println("Note: Parameter instance is " +
						"ignored because there was already an instance " +
						"defined.");
					continue;
				}

				String hodnota = súbor.čítajVlastnosť(skrátená, (String)null);
				if (null != hodnota)
				{
					parameters.setProperty(skrátená, hodnota);
					// System.out.println("  value: " + hodnota);
				}
			}
		}
		finally
		{
			súbor.vynorMennýPriestorVlastností();
		}

		súbor.vnorMennýPriestorVlastností("user");
		try
		{
			user = súbor.čítajVlastnosť("name", user);
			password = súbor.čítajVlastnosť("password", password);
			domain = súbor.čítajVlastnosť("domain", domain);
		}
		finally
		{
			súbor.vynorMennýPriestorVlastností();
		}

		súbor.vnorMennýPriestorVlastností("property");
		try
		{
			for (String vlastnosť : vlastnosti)
			{
				if (null == vlastnosť || vlastnosť.isEmpty() ||
					vlastnosť.startsWith(";") ||
					!vlastnosť.startsWith("property.")) continue;

				String skrátená = vlastnosť.substring("property.".length());
				// System.out.println("property: " + skrátená);

				if (skrátená.isEmpty() || skrátená.equals("name") ||
					skrátená.equals("password") || skrátená.equals("domain"))
				{
					System.err.println("Warning: Invalid property: " +
						skrátená + "\nIt will be ignored.");
					continue;
				}

				String hodnota = súbor.čítajVlastnosť(skrátená, (String)null);

				if (null != hodnota)
				{
					additionalProperties.setProperty(skrátená, hodnota);
					// System.out.println("  value: " + hodnota);
				}
			}
		}
		finally
		{
			súbor.vynorMennýPriestorVlastností();
		}
	}

	private void init()
	{
		disconnect();

		if (null != protocol && protocol.isEmpty())
		{
			error("The protocol must not be omitted.");
			return;
		}

		if (null != server && server.isEmpty())
		{
			error("The server name must not be omitted.");
			return;
		}

		StringBuffer urlBuilder = (null == protocol) ?
			new StringBuffer("jdbc:jtds:sqlserver://") :
			new StringBuffer(protocol + "://");

		if (null == server)
			urlBuilder.append("localhost");
		else
			urlBuilder.append(server);

		urlBuilder.append(':');
		if (null == port) urlBuilder.append("1433");
		else urlBuilder.append(port);

		urlBuilder.append('/');
		if (null != database) urlBuilder.append(database);

		/*if (null != sendStringParametersAsUnicode)
		{
			urlBuilder.append(";sendStringParametersAsUnicode=");
			urlBuilder.append(sendStringParametersAsUnicode);
		}*/

		if (null != instance)
		{
			urlBuilder.append(";instance=");
			urlBuilder.append(instance);
		}

		Set<Entry<Object, Object>> entrySet = parameters.entrySet();
		for (Entry<Object, Object> entry : entrySet)
		{
			urlBuilder.append(';');
			urlBuilder.append(entry.getKey());
			urlBuilder.append('=');
			urlBuilder.append(entry.getValue());
		}

		connectionURL = urlBuilder.toString();

		connectionProperties.clear();
		connectionProperties.setProperty("user", user);
		connectionProperties.setProperty("password", password);
		connectionProperties.setProperty("domain", "uk");

		connectionProperties.putAll(additionalProperties);


		/* TEST * /
		System.out.println("Connect:");
		System.out.println("  " + connectionURL);
		System.out.println("\nProperties:");

		Set<Entry<Object, Object>> entrySet2 = connectionProperties.entrySet();
		for (Entry<Object, Object> entry : entrySet2)
		{
			System.out.println(entry.getKey());
			System.out.println(" = " + entry.getValue());
		}

		if (true) return;
		/* TEST */


		splitHtml();
		// System.out.println("sb0: " + body);

		try
		{
			DriverManager.registerDriver(new Driver());
		}
		catch (SQLException e)
		{
			// throw new Error("Driver registration error", e);
			error(e);
		}
		finally
		{
			// String join = joinHtml(); // (The old way.)
			// System.out.println("sb1: " + join);
			// blok.html(join);
			joinHtml();
		}
	}

	private void connect()
	{
		disconnect();

		splitHtml();

		if (null == connectionURL)
		{
			error("Connection data is not initialised.");
			joinHtml();
			return;
		}

		try
		{
			connection = DriverManager.getConnection(
				connectionURL, connectionProperties);
			statement = connection.createStatement();
		}
		catch (SQLException e)
		{
			// throw new Error("Connection error", e);
			error(e);
		}
		finally
		{
			// String join = joinHtml(); // (The old way.)
			// System.out.println("sb3: " + join);
			// blok.html(join);

			joinHtml();
			if (!isConnected()) disconnect();
		}
	}

	private boolean isConnected()
	{
		return null != connection && null != statement;
	}

	private void disconnect()
	{
		if (null != connection || null != statement)
		{
			splitHtml();

			if (null != statement)
			{
				try { statement.close(); }
				catch (SQLException e) { error(e); }
				statement = null;
			}

			if (null != connection)
			{
				try { connection.close(); }
				catch (SQLException e) { error(e); }
				connection = null;
			}

			joinHtml();
		}
	}

	private void generateControls(int page)
	{
		int size = queryData.size();
		int pages = 1 + ((size - 1) / pageRows);
		if (pages <= 1) return;

		body.append("<p class=\"controls\"><a href=\"jqcmd:page 1\">1</a>");
		for (int i = 2; i <= pages; ++i)
		{
			body.append("   <a href=\"jqcmd:page ");
			body.append(i);
			body.append("\">");
			body.append(i);
			body.append("</a>");
		}
		body.append("   (<a href=\"jqcmd:sel page\">page</a>: ");
		body.append(page);
		body.append(" / ");
		body.append(pages);
		body.append("; ");
		body.append(1 + ((page - 1) * pageRows));
		body.append(" – ");
		int last = page * pageRows;
		body.append(last > size ? size : last);
		body.append(")");
		body.append("</p>");
	}

	private void generateLastQuery()
	{
		if (null == lastQuery) return;
		body.append("<p class=\"last_query\">Last query: ");
		body.append(replaceHTMLEntities(lastQuery));
		body.append("</p>");
	}

	private void generateSummary()
	{
		body.append("<p class=\"summary\">Number of rows: ");
		body.append(queryData.size());
		body.append("</p>");
	}

	private void generateTable(int start, int finish)
	{
		int size = queryData.size();
		if (start < 0) start = 0;
		if (finish < 0 || finish > size) finish = size;

		table.setLength(0);
		table.append("<table border=\"0\" cellpadding=\"0\" " +
			"cellspacing=\"0\" class=\"frame\"><tr><td><table " +
			"border=\"0\" cellpadding=\"2\" cellspacing=\"1\" " +
			"class=\"result\">\n");

		if (null != header)
		{
			table.append("<tr>\n\t");

			int length = header.length;
			for (int j = 0; j < length; ++j)
			{
				table.append("<th>");
				table.append(replaceHTMLEntities(header[j]));
				table.append("</th>");
			}

			table.append("\n</tr>");
		}

		for (int i = start; i < finish; ++i)
		{
			table.append("\n<tr>\n\t");

			String[] row = queryData.get(i);
			if (null == row) continue;

			int length = row.length;
			for (int j = 0; j < length; ++j)
			{
				String content = row[j];

				table.append("<td>");
				if (null == content || content.isEmpty())
					table.append("&#8288;");
					// table.append(" ");
				else
					table.append(replaceHTMLEntities(content));
				table.append("</td>");
			}

			table.append("\n</tr>");
		}

		table.append("\n</table></td></tr></table>");
	}

	private void doQuery(String query)
	{
		// splitHtml();
		// System.out.println("sb2: " + body);

		if (!isConnected())
		{
			error("Database is not connected.");
			joinHtml();
			return;
		}

		try
		{
			ResultSet rs = statement.executeQuery(query);
			ResultSetMetaData rsmd = rs.getMetaData();

			lastQuery = query;

			int numberOfColumns = rsmd.getColumnCount();
			header = new String[numberOfColumns];
			{
				for (int i = 1; i <= numberOfColumns; ++i)
				{
					header[i - 1] = rsmd.getColumnName(i);
					// System.out.print(rsmd.getColumnName(i) + "\t");
				}
				// System.out.println();
			}

			queryData.clear();
			String[] row;

			while (rs.next())
			{
				row = new String[numberOfColumns];
				for (int i = 1; i <= numberOfColumns; ++i)
					row[i - 1] = rs.getString(i);
					// System.out.print(rs.getString(i) + "\t");
				queryData.add(row);
				// System.out.println();
			}

			page(1);
			// generateLastQuery();
			// generateControls(1);
			// generateTable(0, pageRows);
			// body.append(table);
			// generateControls(1);
			// generateSummary();
		}
		catch (SQLException e)
		{
			// throw new Error("Query error", e);
			error(e);
		}
		finally
		{
			// String join = joinHtml(); // (The old way.)
			// System.out.println("sb3: " + join);
			// blok.html(join);
			// joinHtml();
		}
	}

	private void onCommand()
	{
		String command = ÚdajeUdalostí.príkazSkratky();
		if (clear1 == command || clear2 == command) clear();
	}

	public void selPage()
	{
		Svet.správa("Not implemented, yet."); // TODO
	}

	public void page(int page)
	{
		// System.out.println("page: " + page);
		clear();
		splitHtml();
		generateLastQuery();
		generateControls(page);
		generateTable((page - 1) * pageRows, page * pageRows);
		// System.out.println("generate: " + ((page - 1) * pageRows) + " – " + (page * pageRows));
		body.append(table);
		generateControls(page);
		generateSummary();
		joinHtml();
	}

	private void openURL(String url)
	{
		// System.out.println("url: " + url);
		if (url.startsWith("jqcmd:"))
		{
			if (!vykonajPríkaz(url.substring(6)))
				Svet.chyba(Svet.textPoslednejChyby());
			// TODO prelož dialógy
		}
		else
			Svet.otvorWebovýOdkaz(url);
	}


	public static void main(String[] args)
	{
		Svet.konfiguračnýSúbor().prekladajVlastnosti(new String[][]{
			{"okno.", "window.", "Z"},
			{"história.", "history.", "Z"},
			{".riadok[", ".line[", "S"},
			{".dĺžka", ".size", "K"},
			{".šírka", ".width", "K"},
			{".výška", ".height", "K"},
			{".maximalizované", ".maximized", "K"},
			{".minimalizované", ".minimized", "K"}
		}, new String[][]{
			{"žiadna", "none", "P"}, {"biela", "white", "P"},
			{"svetlošedá", "lightgray", "P"}, {"šedá", "gray", "P"},
			{"tmavošedá", "darkgray", "P"}, {"čierna", "black", "P"},
			{"svetločervená", "lightred", "P"}, {"červená", "red", "P"},
			{"tmavočervená", "darkred", "P"},
			{"svetlozelená", "lightgreen", "P"},
			{"zelená", "green", "P"}, {"tmavozelená", "darkgreen", "P"},
			{"svetlomodrá", "lightblue", "P"}, {"modrá", "blue", "P"},
			{"tmavomodrá", "darkblue", "P"},
			{"svetlotyrkysová", "lightcyan", "P"},
			{"tyrkysová", "cyan", "P"}, {"tmavotyrkysová", "darkcyan", "P"},
			{"svetlopurpurová", "lightmagenta", "P"},
			{"purpurová", "magenta", "P"},
			{"tmavopurpurová", "darkmagenta", "P"},
			{"svetložltá", "lightyellow", "P"}, {"žltá", "yellow", "P"},
			{"tmavožltá", "darkyellow", "P"},
			{"svetlohnedá", "lightbrown", "P"},
			{"hnedá", "brown", "P"}, {"tmavohnedá", "darkbrown", "P"},
			{"svetlooranžová", "lightorange", "P"},
			{"oranžová", "orange", "P"}, {"tmavooranžová", "darkorange", "P"},
			{"svetloružová", "lightpink", "P"},
			{"ružová", "pink", "P"}, {"tmavoružová", "darkpink", "P"},
			{"uhlíková", "coal", "P"}, {"antracitová", "anthracite", "P"},
			{"papierová", "paper", "P"}, {"snehová", "snow", "P"},
			{"tmavofialová", "darkpurple", "P"}, {"fialová", "purple", "P"},
			{"svetlofialová", "lightpurple", "P"},
			{"tmavoatramentová", "darkink", "P"}, {"atramentová", "ink", "P"},
			{"svetloatramentová", "lightink", "P"},
			{"tmavoakvamarínová", "darkaqua", "P"},
			{"akvamarínová", "aqua", "P"},
			{"svetloakvamarínová", "lightaqua", "P"},
			{"tmaváNebeská", "darkceleste", "P"}, {"nebeská", "celeste", "P"},
			{"svetláNebeská", "lightceleste", "P"}
		});

		Svet.režimLadenia(true, true);
		Svet.použiKonfiguráciu("config.cfg");
		Svet.skry(); Svet.nekresli();
		try { new JDBCQuerier(); }
		catch (Throwable t) { t.printStackTrace(); }
		finally { Svet.zobraz(); }
	}
}
