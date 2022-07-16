
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

import java.util.regex.Matcher;
// import java.util.regex.PatternSyntaxException; // IllegalArgumentException
import static java.util.regex.Pattern.*;

import knižnica.*;

import net.sourceforge.jtds.jdbc.Driver;

/*
 * (See the config-example.cfg for available configuration directives and
 * some tips.)
 */

public class JDBCQuerier extends GRobot
{
	private final static String title = "JDBC Querier";
	private final static String version = "1.0";

	private final static String clear1 = "clear1";
	private final static String clear2 = "clear2";
	private final static String fullscr = "fullscr";
	private final static String export = "export";
	private final static String help = "help";

	private final static String defaultStyle =
		// "/* Default style. */\n" +
		"body { font-family: Verdana, sans-serif; }\n" +
		"p { margin: 0px; }\n" +
		"p.error { font-weight: bold; color: maroon; }\n" +
		"p.last_query { color: purple; }\n" +
		"p.controls { text-align: center; }\n" +
		"p.summary { color: navy; }\n" +
		"table.frame { background-color: black; }\n" +
		"table.result tr th, table.result tr td " +
			"{ background-color: white; }\n";

	private final static String defaultBody = "";
		// "<!-- Default body -->";


	private final StringBuffer head = new StringBuffer(),
		body = new StringBuffer(), exportBuffer = new StringBuffer();

	private StringBuffer buffer = body;

	private final Properties parameters = new Properties();
	private final Properties connectionProperties = new Properties();
	private final Properties additionalProperties = new Properties();

	private String server = null;
	private String protocol = null;
	private String port = null;
	private String database = null;
	private String instance = null;

	private String connectionURL = null;

	private String user = null;
	private String password = null;
	private String domain = null;

	private Connection connection = null;
	private Statement statement = null;

	private final PoznámkovýBlok blok;

	private final PoložkaPonuky clearItem;
	private final PoložkaPonuky exportItem;
	private final PoložkaPonuky fullscreenItem;
	private final PoložkaPonuky helpItem;
	private final PoložkaPonuky englishItem;

	private final PoložkaPonuky generateTranslationItem;

	private final Zoznam<PoložkaPonuky> languages = new Zoznam<>();

	private boolean fullscreen = false;

	private String language = null;
	private int pageRows = 25;

	private boolean configChanged = false;

	private String lastQuery = null;
	private int lastDisplayedCount = 0;
	private int lastPage = 1;

	private String[] queryHead = null;
	private final ArrayList<String[]> queryData = new ArrayList<>();


	private static class Pattern
	{
		private java.util.regex.Pattern pattern;
		private String replace;

		public Pattern(String regex, String replace)
			// throws IllegalArgumentException, PatternSyntaxException
		{
			pattern = compile(regex, CASE_INSENSITIVE | UNICODE_CASE);
			if (null == replace) this.replace = "";
			else this.replace = replace;
		}

		public String match(String input)
		{
			Matcher matcher = pattern.matcher(input);
			if (matcher.matches()) return matcher.replaceAll(replace);
			return null;
		}

		public boolean matches(String input)
		{
			return pattern.matcher(input).matches();
		}

		public Matcher matcher(String input)
		{
			return pattern.matcher(input);
		}
	}

	private final ArrayList<Pattern> patterns = new ArrayList<>();

	private final Pattern helpCommand =
		new Pattern("^\\s*help\\s*;?\\s*$", null);
	private final Pattern clearCommand =
		new Pattern("^\\s*clear\\s*;?\\s*$", null);
	private final Pattern pageCommand0 = new Pattern("^\\s*list\\s+page\\s*" +
		";?\\s*$", "$1");
	private final Pattern pageCommand1 = new Pattern("^\\s*list\\s+page\\s+" +
		"([0-9]+)\\s*;?\\s*$", "$1");
	private final Pattern rowsCommand0 = new Pattern("^\\s*list\\s+rows\\s*" +
		";?\\s*$", "$1 $2");
	private final Pattern rowsCommand1 = new Pattern("^\\s*list\\s+rows\\s+" +
		"([0-9]+)\\s*;?\\s*$", "$1 $2");
	private final Pattern rowsCommand2 = new Pattern("^\\s*list\\s+rows\\s+" +
		"([0-9]+)[\\s,]+([0-9]+)\\s*;?\\s*$", "$1 $2");
	private final Pattern exportCommand0 =
		new Pattern("^\\s*export\\s*;?\\s*$", null);
	private final Pattern exportCommand1 =
		new Pattern("^\\s*export\\s*\"([^\"]*)\"\\s*;?\\s*$", null);
	private final Pattern exportCommand2 =
		new Pattern("^\\s*export\\s*\"([^\"]*)\"\\s*,\\s*(true|false)" +
			"\\s*;?\\s*$", null);

	private final Pattern getRows = new Pattern(
		"^\\s*([0-9]+)[-–,;\\s]+([0-9]+)\\s*;?\\s*$", "$1 $2");


	private JDBCQuerier()
	{
		super(Svet.šírkaZariadenia(), Svet.výškaZariadenia(),
			title + " " + version);
		skry();
		// interaktívnyRežim(true); // NOPE

		Svet.začniVstup();
		Svet.neskrývajVstupnýRiadok();
		Svet.aktivujHistóriuVstupnéhoRiadka();
		Svet.uchovajHistóriuVstupnéhoRiadka();
		Svet.hlavnýPanel().setEnabled(false);

		blok = new PoznámkovýBlok();
		blok.roztiahniNaŠírku();
		blok.roztiahniNaVýšku();
		blok.zrušDekor(false);
		blok.neupravuj();

		Svet.pridajKlávesovúSkratku(clear1, Kláves.VK_L);
		Svet.pridajKlávesovúSkratku(clear2, Kláves.VK_K);
		Svet.pridajKlávesovúSkratku(export, Kláves.VK_E);
		Svet.pridajKlávesovúSkratku(fullscr, Kláves.VK_F11, 0);
		Svet.pridajKlávesovúSkratku(help, Kláves.VK_F1, 0);

		clearItem = new PoložkaPonuky("C", Kláves.VK_C);
		exportItem = new PoložkaPonuky("E", Kláves.VK_E);
		fullscreenItem = new PoložkaPonuky("L", Kláves.VK_L);
		helpItem = new PoložkaPonuky("H", Kláves.VK_H);

		clearItem.príkaz(clear1);
		exportItem.príkaz(export);
		fullscreenItem.príkaz(fullscr);
		helpItem.príkaz(help);

		// This part is not translated:
		Svet.pridajPoložkuHlavnejPonuky("Languages", Kláves.VK_L); // [A⽂]
		englishItem = new PoložkaPonuky("English");
		englishItem.klávesováSkratka(Kláves.VK_H);
		languages.pridaj(englishItem);
		Svet.pridajOddeľovačPonuky();

		new ObsluhaUdalostí()
		{
			@Override public void čítajKonfiguráciu(Súbor súbor)
				throws IOException { loadConfig(súbor); }

			@Override public void zapíšKonfiguráciu(Súbor súbor)
				throws IOException { saveConfig(súbor); }

			@Override public boolean konfiguráciaZmenená()
			{ return configChanged; }

			@Override public void potvrdenieÚdajov()
			{ doQuery(Svet.prevezmiReťazec()); }

			@Override public void ukončenie() { disconnect(); }

			@Override public void klávesováSkratka() { onCommand(); }

			@Override public void aktiváciaOdkazu()
			{ openURL(ÚdajeUdalostí.poslednýOdkaz()); }

			@Override public void voľbaPoložkyPonuky() { onMenu(); }
		};

		translate(language);

		try {
			String[] langFiles = Súbor.zoznamSúborov(".");
			for (String langFile : langFiles)
			{
				if (null == langFile || langFile.isEmpty() ||
					!langFile.endsWith(".lng")) continue;
				langFile = langFile.substring(0, langFile.length() - 4);
				if ("English".equals(langFile)) continue;
				PoložkaPonuky langItem = new PoložkaPonuky(langFile);
				languages.pridaj(langItem);
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}

		Svet.pridajOddeľovačPonuky();
		generateTranslationItem = new PoložkaPonuky(
			"Generate new .lng file…", Kláves.VK_G);

		try {
			Súbor súbor = new Súbor();
			súbor.otvorNaČítanie("config.cfg");
			loadSetup(súbor); // (renamed due to conflict with window config)
			súbor.zavri();
			súbor = null;
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}

		clear();
		init();
		connect();
	}


	private String replaceHTMLEntities(String text)
	{
		return text.replace("&", "&amp;")
			.replace("<", "&lt;").replace(">", "&gt;")
			.replace("\"", "&quot;")
			// .replace("'", "&apos;")
				// The &apos; entity is not processed by text the pane
				// correctly… (Probably a bug.)
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
						head.append(html.substring(indexOf1 + 1, indexOf2));
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
						body.append(html.substring(indexOf1 + 1, indexOf2));
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

	private void error(SQLException t)
	{
		boolean joinFlag;
		if (!splitFlag)
		{
			splitHtml();
			joinFlag = true;
		}
		else joinFlag = false;

		// t.printStackTrace(); // Could be enabled for testing purposes,
			// but there is literally a minuscule chance of getting any more
			// relevant information from the stack trace of the SQLException…

		body.append("\n<p class=\"error\">");
		body.append(replaceHTMLEntities(t.getMessage()).replace("\n", "<br>"));
		body.append("</p>\n");

		/*
		 * I wanted to write out the whole exception here (for any case).
		 * 
		 * 
		 * The content should’ve been “hideable,” but the hidden style
		 * (display:none) is not supported by the (slow and obsolete) HTML
		 * engine incorporated inside the Java text pane that I used.
		 * 
		 * … <pre style=\"display:none;\">");	// (would be joined with
		 * 										// previous append)
		 * body.append(replaceHTMLEntities(
		 * 	GRobotException.stackTraceToString(t)));
		 * body.append("</pre>\n");
		 */

		if (joinFlag) joinHtml();
	}

	private void error(String s)
	{
		boolean joinFlag;
		if (!splitFlag)
		{
			splitHtml();
			joinFlag = true;
		}
		else joinFlag = false;

		System.err.println(s);
		body.append("\n<p class=\"error\">");
		body.append(replaceHTMLEntities(s).replace("\n", "<br>"));
		body.append("</p>\n");

		if (joinFlag) joinHtml();
	}

	private static void errorMessage(String s)
	{ Svet.chyba(s, title + titleSeparator + errorTitle); }

	private static void message(String s) { Svet.správa(s, title); }

	private static boolean question(String s)
	{ return ÁNO == Svet.otázka(s, title + titleSeparator + questionTitle); }

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

	private void fullscreen()
	{
		if (Svet.celáObrazovka(fullscreen = !fullscreen))
			Svet.aktivujVstupnýRiadok(); else Svet.pípni();
	}


	private void loadConfig(Súbor súbor) throws IOException
	{
		language = súbor.čítajVlastnosť("language", language);
		configChanged = false;
	}

	private void saveConfig(Súbor súbor) throws IOException
	{
		súbor.zapíšVlastnosť("language", language);
		configChanged = false;
	}

	private void loadSetup(Súbor súbor) throws IOException
	{
		// This config contains the necessary data about server and user,
		// and another configuration like patterns, number of rows per page…

		parameters.clear();
		connectionProperties.clear();
		additionalProperties.clear();

		server = null;
		protocol = null;
		port = null;
		database = null;
		instance = null;

		connectionURL = null;

		user = null;
		password = null;
		domain = null;

		Zoznam<String> vlastnosti = súbor.zoznamVlastností();

		/*
		 * Note: Those tries without catches (below) serve one purpose only –
		 *     to restore the last namespace (menný priestor) in case of
		 *     failure (in the finally blocks).
		 */

		súbor.vnorMennýPriestorVlastností("server");
		try
		{
			server = súbor.čítajVlastnosť("name", server);
			protocol = súbor.čítajVlastnosť("protocol", protocol);
			port = súbor.čítajVlastnosť("port", port);
			database = súbor.čítajVlastnosť("database", database);
			instance = súbor.čítajVlastnosť("instance", instance);
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

		súbor.vnorMennýPriestorVlastností("parameter");
		try
		{
			for (String vlastnosť : vlastnosti)
			{
				if (null == vlastnosť || vlastnosť.isEmpty() ||
					vlastnosť.startsWith(";") ||
					!vlastnosť.startsWith("parameter.")) continue;

				String skrátená = vlastnosť.substring("parameter.".length());

				if (skrátená.isEmpty() || skrátená.equals("name") ||
					skrátená.equals("protocol") || skrátená.equals("port"))
				{
					System.err.println(warningLabel + ": " +
						invalidParameterError + ": " + skrátená + "\n" +
						ignoredNotice);
					continue;
				}

				if (skrátená.equals("database") && null != database)
				{
					System.err.println(noteLabel + ": " + parameterDefinedNote +
						"database" + " " + ignoredNotice);
					continue;
				}

				if (skrátená.equals("instance") && null != instance)
				{
					System.err.println(noteLabel + ": " + parameterDefinedNote +
						"instance" + " " + ignoredNotice);
					continue;
				}

				String hodnota = súbor.čítajVlastnosť(skrátená, (String)null);
				if (null != hodnota) parameters.setProperty(skrátená, hodnota);
			}
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

				if (skrátená.isEmpty() || skrátená.equals("name") ||
					skrátená.equals("password") || skrátená.equals("domain"))
				{
					System.err.println(warningLabel + ": " +
						invalidPropertyError + ": " + skrátená + "\n" +
						ignoredNotice);
					continue;
				}

				String hodnota = súbor.čítajVlastnosť(skrátená, (String)null);
				if (null != hodnota)
					additionalProperties.setProperty(skrátená, hodnota);
			}
		}
		finally
		{
			súbor.vynorMennýPriestorVlastností();
		}

		súbor.vnorMennýPriestorVlastností("pattern");
		try
		{
			int count = súbor.čítajVlastnosť("count", -1);
			if (count >= 0)
			{
				for (int i = 0; i < count; ++i)
				{
					String regex = súbor.čítajVlastnosť(
						"regex[" + i + "]", (String)null);
					String replace = súbor.čítajVlastnosť(
						"replace[" + i + "]", (String)null);

					if (null != regex && !regex.isEmpty())
					try
					{
						Pattern pattern = new Pattern(regex, replace);
						patterns.add(pattern);
					}
					catch (IllegalArgumentException iae)
					{
						iae.printStackTrace();
					}
				}
			}
		}
		finally
		{
			súbor.vynorMennýPriestorVlastností();
		}

		súbor.vnorMennýPriestorVlastností("config");
		try
		{
			pageRows = súbor.čítajVlastnosť("pageRows", pageRows);
			if (pageRows < 10) pageRows = 10;
			if (pageRows > 200) pageRows = 200;
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
			error(emptyProtocolError);
			return;
		}

		if (null != server && server.isEmpty())
		{
			error(emptyServerNameError);
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

		try
		{
			DriverManager.registerDriver(new Driver());
		}
		catch (SQLException e)
		{
			// Driver registration error:
			error(e);
		}
		finally
		{
			joinHtml();
		}
	}

	private void connect()
	{
		disconnect();

		splitHtml();

		if (null == connectionURL)
		{
			error(connectionNotInitializedError);
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
			// Connection error:
			error(e);
		}
		finally
		{
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
		if (pages <= 1)
		{
			buffer.append(
				"<p class=\"controls\">(<a href=\"jqcmd:sel rows\">");
			buffer.append(selectRowsLabel);
			buffer.append("</a>)</p>");
			return;
		}

		buffer.append("<p class=\"controls\">");

		if (1 == page)
			buffer.append("&lt;   1");
		else
		{
			buffer.append("<a href=\"jqcmd:list page ");
			buffer.append(page - 1);
			buffer.append("\">&lt;</a>   <a href=\"jqcmd:list page 1\">1</a>");
		}

		int intervalA = page - 3, intervalB = page + 3;

		for (int i = 2; i <= pages; ++i)
		{
			buffer.append("   ");

			if (i > 3 && i < intervalA)
			{
				buffer.append("…");
				i = intervalA - 1;
			}
			else if (i > intervalB && i < pages - 3)
			{
				buffer.append("…");
				i = pages - 4;
			}
			else if (page == i)
				buffer.append(i);
			else
			{
				buffer.append("<a href=\"jqcmd:list page ");
				buffer.append(i);
				buffer.append("\">");
				buffer.append(i);
				buffer.append("</a>");
			}
		}

		if (page == pages)
			buffer.append("   &gt;");
		else
		{
			buffer.append("   <a href=\"jqcmd:list page ");
			buffer.append(page + 1);
			buffer.append("\">&gt;</a>");
		}

		buffer.append("   (<a href=\"jqcmd:sel page\">");
		buffer.append(pageLabel);
		buffer.append("</a>: ");
		buffer.append(page);
		buffer.append(" / ");
		buffer.append(pages);
		buffer.append("; <a href=\"jqcmd:sel rows\">");
		buffer.append(rowsLabel);
		buffer.append("</a>: ");
		buffer.append(1 + ((page - 1) * pageRows));
		buffer.append(" – ");
		int last = page * pageRows;
		buffer.append(last > size ? size : last);
		buffer.append(")</p>");
	}

	private void generateControls(int start, int finish)
	{
		int size = queryData.size();
		int pages = 1 + ((size - 1) / pageRows);

		buffer.append("<p class=\"controls\">");

		if (pages > 0)
		{
			buffer.append("<a href=\"jqcmd:list page 1\">1</a>");

			for (int i = 2; i <= pages; ++i)
			{
				buffer.append("   ");

				if (i > 3 && i < pages - 3)
				{
					buffer.append("…");
					i = pages - 4;
				}
				else
				{
					buffer.append("<a href=\"jqcmd:list page ");
					buffer.append(i);
					buffer.append("\">");
					buffer.append(i);
					buffer.append("</a>");
				}
			}

			buffer.append("   (<a href=\"jqcmd:sel page\">");
			buffer.append(selectPageLabel);
			buffer.append("</a>; ");
		}
		else buffer.append("(");

		buffer.append("<a href=\"jqcmd:sel rows\">");
		buffer.append(rowsLabel);
		buffer.append("</a>: ");
		buffer.append(1 + start);
		buffer.append(" – ");
		buffer.append(finish > size ? size : finish);
		buffer.append(")</p>");
	}

	private void generateLastQuery()
	{
		if (null == lastQuery) return;
		buffer.append("<p class=\"last_query\">");
		buffer.append(lastQueryLabel);
		buffer.append(": ");
		buffer.append(replaceHTMLEntities(lastQuery));
		buffer.append("</p>");
	}

	private void generateSummary()
	{
		int size = queryData.size();
		buffer.append("<p class=\"summary\">");
		if (lastDisplayedCount != size)
		{
			buffer.append(displayedRowsLabel);
			buffer.append(": ");
			buffer.append(lastDisplayedCount);
			buffer.append("<br>");
		}
		buffer.append(totalRowsLabel);
		buffer.append(": ");
		buffer.append(size);
		buffer.append("</p>");
	}

	private void generateTable(int start, int finish)
	{
		int size = queryData.size();
		if (start < 0) start = 0;
		if (finish < 0 || finish > size) finish = size;

		buffer.append("<table border=\"0\" cellpadding=\"0\" " +
			"cellspacing=\"0\" class=\"frame\"><tr><td><table " +
			"border=\"0\" cellpadding=\"2\" cellspacing=\"1\" " +
			"class=\"result\">\n");

		if (null != queryHead)
		{
			buffer.append("<tr>\n\t<th>&#8288;</th>");

			int length = queryHead.length;
			for (int j = 0; j < length; ++j)
			{
				buffer.append("<th>");
				buffer.append(replaceHTMLEntities(queryHead[j]));
				buffer.append("</th>");
			}

			buffer.append("\n</tr>");
		}

		lastDisplayedCount = 0;

		for (int i = start; i < finish; ++i)
		{
			buffer.append("\n<tr>\n\t");

			String[] row = queryData.get(i);
			if (null == row) continue;

			++lastDisplayedCount;
			buffer.append("<td>");
			buffer.append(i + 1);
			buffer.append(".</td>");

			int length = row.length;
			for (int j = 0; j < length; ++j)
			{
				String content = row[j];

				buffer.append("<td>");
				if (null == content || content.isEmpty())
					buffer.append("&#8288;");
				else
					buffer.append(replaceHTMLEntities(content));
				buffer.append("</td>");
			}

			buffer.append("\n</tr>");
		}

		buffer.append("\n</table></td></tr></table>");
	}

	private void doQuery(String query)
	{
		// TODO:
		//	Help for custom commands:
		//		help;
		//		clear;
		//		list page [n];
		//		list rows [m[, n]];
		//		export ["filename"[, autooverwrite]];

		if (!isConnected())
		{
			error(notConnectedError);
			return;
		}

		if (helpCommand.matches(query))
		{
			help();
			return;
		}

		if (clearCommand.matches(query))
		{
			clear();
			return;
		}

		{
			String match = pageCommand1.match(query);
			if (null != match)
			{
				listPage(match);
				return;
			}

			Matcher matcher = rowsCommand2.matcher(query);
			if (matcher.matches())
			{
				listRows(matcher);
				return;
			}

			matcher = rowsCommand1.matcher(query);
			if (matcher.matches())
			{
				listRows(matcher);
				return;
			}

			matcher = exportCommand2.matcher(query);
			if (matcher.matches())
			{
				export(matcher);
				return;
			}

			matcher = exportCommand1.matcher(query);
			if (matcher.matches())
			{
				export(matcher);
				return;
			}
		}

		if (pageCommand0.matches(query))
		{
			listPage();
			return;
		}

		if (rowsCommand0.matches(query))
		{
			listRows();
			return;
		}

		if (exportCommand0.matches(query))
		{
			export();
			return;
		}

		// System.out.println("Query: " + query);

		for (Pattern pattern : patterns)
		{
			String match = pattern.match(query);
			if (null != match)
			{
				if (match.isEmpty())
				{
					error(patternGotEmptyStringError);
					return;
				}
				query = match;
				break;
			}
		}

		try
		{
			ResultSet rs = statement.executeQuery(query);
			ResultSetMetaData rsmd = rs.getMetaData();

			lastQuery = query;

			int numberOfColumns = rsmd.getColumnCount();
			queryHead = new String[numberOfColumns];
			for (int i = 1; i <= numberOfColumns; ++i)
				queryHead[i - 1] = rsmd.getColumnName(i);

			queryData.clear();
			String[] row;

			while (rs.next())
			{
				row = new String[numberOfColumns];
				for (int i = 1; i <= numberOfColumns; ++i)
					row[i - 1] = rs.getString(i);
				queryData.add(row);
			}

			listPage(1);
		}
		catch (SQLException e)
		{
			// Query error:
			error(e);
		}
	}

	private void onCommand()
	{
		String command = ÚdajeUdalostí.príkazSkratky();
		if (clear1 == command || clear2 == command) clear();
		else if (export == command) export();
		else if (fullscr == command) fullscreen();
		else if (help == command) help();
	}

	private void onMenu()
	{
		for (PoložkaPonuky langItem : languages)
			if (langItem.zvolená())
			{
				String langString = langItem.text();
				if ("English".equals(langString)) langString = null;

				if (((null == langString || null == language) &&
					langString != language) ||
					((null != langString && null != language) &&
					!langString.equals(language)))
					configChanged = true;

				translate(langString);
				language = langString;
				return;
			}

		if (generateTranslationItem.zvolená())
			generateTranslationItem();
	}

	public void listPage() { listPage(lastPage); }
	public void listPage(int page)
	{
		int size = queryData.size();
		int pages = 1 + ((size - 1) / pageRows);
		if (page < 1) page = 1;
		else if (page > pages) page = pages;

		clear();
		splitHtml();
		generateLastQuery();

		generateControls(page);
		generateTable((page - 1) * pageRows, page * pageRows);
		generateControls(page);

		generateSummary();
		joinHtml();
		lastPage = page;
	}

	public void listPage(String page)
	{
		Long number = Svet.reťazecNaCeléČíslo(page);
		if (null == number)
			error(invalidPageNumberError + ": " + page);
		else
			listPage(number.intValue());
	}

	public void selPage()
	{
		Long newPage = Svet.upravCeléČíslo(lastPage,
			enterPageLabel + ":", title);
		if (null != newPage)
			listPage(newPage.intValue());
	}

	public void listRows() { selRows(); }
	public void listRows(int start)
	{
		String range = Svet.upravReťazec(start + "–",
			enterRowsLabel + ":", title);
		if (null != range)
		{
			Matcher matcher = getRows.matcher(range);
			if (matcher.matches()) listRows(matcher);
		}
	}

	public void listRows(int start, int finish)
	{
		--start; int size = queryData.size();
		if (start < 0) start = 0;
		if (finish < 0 || finish > size) finish = size;

		if (start < finish)
		{
			if (finish - start > 100 && !question(
				"<html><b>" + warningLabel + "!</b><br> <br>" +
				bigRangeIsSlowWarning.replace("\n", "<br>") + " " +
				considerExportLabel.replace("\n", "<br>") + "<br> <br><b>" +
				wantSlowBigRangeQuestion.replace("\n", "<br>") +
				"</b><br> </html>")) return;

			clear();
			splitHtml();
			generateLastQuery();

			generateControls(start, finish);
			generateTable(start, finish);
			generateControls(start, finish);

			generateSummary();
			joinHtml();
		}
	}

	public void listRows(Matcher matcher)
	{
		String starting = "";
		String finishing = "";

		if (matcher.groupCount() < 2)
		{
			if (matcher.groupCount() >= 1) starting = matcher.group(1);
			finishing = Svet.upravReťazec(starting + "–",
				enterRowsLabel + ":", title);
			if (null == finishing) return;
			matcher = getRows.matcher(finishing);
			if (!matcher.matches()) return;
		}

		if (matcher.groupCount() < 2) return;

		starting = matcher.group(1);
		finishing = matcher.group(2);

		Long start = Svet.reťazecNaCeléČíslo(starting);
		if (null == start)
		{
			error(invalidStartingRowError + starting);
			return;
		}

		Long finish = Svet.reťazecNaCeléČíslo(finishing);
		if (null == finish)
		{
			error(invalidFinishingRowError + finishing);
			return;
		}

		listRows(start.intValue(), finish.intValue());
	}

	public void selRows()
	{
		String range = Svet.zadajReťazec(enterRowsLabel + ":", title);
		if (null != range)
		{
			Matcher matcher = getRows.matcher(range);
			if (matcher.matches()) listRows(matcher);
		}
	}

	private void openURL(String url)
	{
		if (url.startsWith("jqcmd:"))
		{
			if (!vykonajPríkaz(url.substring(6)))
				errorMessage(commandExecutionError + "\n" +
					messageInSlovakLabel + ":\n\n" +
					Svet.textPoslednejChyby());
		}
		else
			Svet.otvorWebovýOdkaz(url);
	}

	public void export(Matcher matcher)
	{
		String fileName = matcher.group(1);
		boolean overwrite = (matcher.groupCount() >= 2) ?
			"true".equalsIgnoreCase(matcher.group(2)) : false;
		export(fileName, overwrite);
	}

	public void export() { export(false); }
	public void export(boolean overwrite)
	{
		String fileName = Súbor.dialógUložiť(exportTitle, null,
			exportHTMLFilter + " (*.htm; *.html)");

		export(fileName, overwrite);
	}

	public void export(String fileName) { export(fileName, false); }
	public void export(String fileName, boolean overwrite)
	{
		if (null != fileName)
		{
			String lowerName = fileName.toLowerCase();
			if (!lowerName.endsWith(".htm") && !lowerName.endsWith(".html"))
			{
				fileName += ".html";
				if (Súbor.jestvuje(fileName) && !overwrite &&
					!question("<html><b>" + warningLabel + "!</b><br> <br>" +
					extensionAutoappendWarning.replace("\n", "<br>") +
					"<br> <br><b>" + overwriteQuestion.replace("\n", "<br>") +
					"</b><br> </html>")) return;
			}

			StringBuffer backup = buffer;
			buffer = exportBuffer;
			int lastCountBackup = lastDisplayedCount;

			try
			{
				exportBuffer.setLength(0);

				if (null != lastQuery)
				{
					buffer.append("<p class=\"last_query\">");
					buffer.append(replaceHTMLEntities(lastQuery));
					buffer.append("</p>");
				}

				generateTable(0, queryData.size());

				súbor.otvorNaZápis(fileName);
				súbor.zapíšRiadok("<!DOCTYPE html>");
				súbor.zapíšRiadok("<html>");
				súbor.zapíšRiadok("<head>");
				súbor.zapíšRiadok("<style>");
				súbor.zapíš(defaultStyle.replace("\n", "\r\n"));
				súbor.zapíšRiadok("</style>");
				súbor.zapíšRiadok("</head>");
				súbor.zapíšRiadok("<body>");
				súbor.zapíšRiadok(exportBuffer.toString()
					.replace("\n", "\r\n")
					.replace("<br>", "<br />"));
				súbor.zapíšRiadok("</body>");
				súbor.zapíš("</html>");
				súbor.zavri();

				message(String.format(exportOk, fileName));
			}
			catch (IOException ioe)
			{
				errorMessage(String.format(exportIOFailure, ioe.getMessage()));
			}
			catch (Exception e)
			{
				errorMessage(String.format(exportOtherFailure, e.getMessage()));
			}
			finally
			{
				buffer = backup;
				lastDisplayedCount = lastCountBackup;
			}
		}
	}

	private void help()
	{
		message(
			"Help is not implemented, yet, but you can use\n" +
			"the following commands: clear; list page [#];\n" +
			"list rows [#[, #]]; and export [\"filename\"\n" +
			"[, autooverwrite]].");
	}


	// The translations are initialized in the translate method (below):

	private static String translationAuthors;
	private static String translationDate;

	private static String menuLabel;

	private static String clearLabel;
	private static String exportLabel;
	private static String fullscreenLabel;
	private static String helpLabel;
	private static String exitLabel;

	private static int menuMnemo;

	private static int clearMnemo;
	private static int exportMnemo;
	private static int fullscreenMnemo;
	private static int helpMnemo;
	private static int exitMnemo;

	private static String warningLabel;
	private static String noteLabel;

	private static String yesLabel;
	private static String noLabel;
	private static String okLabel;
	private static String cancelLabel;

	private static String invalidParameterError;
	private static String invalidPropertyError;
	private static String invalidPageNumberError;
	private static String invalidStartingRowError;
	private static String invalidFinishingRowError;

	private static String emptyProtocolError;
	private static String emptyServerNameError;
	private static String connectionNotInitializedError;
	private static String notConnectedError;

	private static String commandExecutionError;

	private static String patternGotEmptyStringError;

	private static String messageInSlovakLabel;

	private static String parameterDefinedNote;
	private static String ignoredNotice;

	private static String bigRangeIsSlowWarning;
	private static String considerExportLabel;

	private static String wantSlowBigRangeQuestion;

	private static String titleSeparator;
	private static String errorTitle;
	private static String questionTitle;
	private static String exportTitle;

	private static String lastQueryLabel;

	private static String selectPageLabel;
	private static String selectRowsLabel;
	private static String pageLabel;
	private static String rowsLabel;

	private static String enterPageLabel;
	private static String enterRowsLabel;

	private static String displayedRowsLabel;
	private static String totalRowsLabel;

	private static String exportHTMLFilter;
	private static String exportIOFailure;
	private static String exportOtherFailure;
	private static String exportOk;

	private static String extensionAutoappendWarning;
	private static String overwriteQuestion;

	private static String translationReadError;


	private final static Súbor translate = new Súbor();

	private static String updateTranslation(String directive, String value)
		throws IOException
	{
		String read = translate.čítajVlastnosť(directive, value);
		if (null == read || read.isEmpty()) return value;
		return read;
	}

	private static int updateMnemo(String directive, int value)
		throws IOException
	{
		String read = translate.čítajVlastnosť(directive, (String)null);
		if (null != read && 1 == read.length())
		{
			char ch = read.toUpperCase().charAt(0);
			if ((ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9'))
				return (int)ch; // VK_A … VK_Z, VK_0 … VK_9
		}
		return value;
	}


	public void translate(String language)
	{
		// (Re)set the default values (missing translations will stay in
		// English):
		translationAuthors = "Roman Horváth";
		translationDate = "2022-07-16";

		menuLabel = "Menu";

		clearLabel = "Clear console";
		exportLabel = "Export data…";
		fullscreenLabel = "Full screen";
		helpLabel = "Help…";
		exitLabel = "Exit";

		menuMnemo = Kláves.VK_M;

		clearMnemo = Kláves.VK_C;
		exportMnemo = Kláves.VK_E;
		fullscreenMnemo = Kláves.VK_L;
		helpMnemo = Kláves.VK_H;
		exitMnemo = Kláves.VK_X;

		warningLabel = "Warning";
		noteLabel = "Note";

		yesLabel = "Yes";
		noLabel = "No";
		okLabel = "Ok";
		cancelLabel = "Cancel";

		invalidParameterError = "Invalid parameter";
		invalidPropertyError = "Invalid property";
		invalidPageNumberError = "Invalid page number";
		invalidStartingRowError = "Invalid starting row number";
		invalidFinishingRowError = "Invalid finishing row number";

		emptyProtocolError = "The protocol must not be omitted.";
		emptyServerNameError = "The server name must not be omitted.";
		connectionNotInitializedError = "Connection data is not initialised.";
		notConnectedError = "Database is not connected.";

		commandExecutionError = "Internal command execution error.";

		patternGotEmptyStringError = "Pattern matching resulted in an " +
			"empty string.\nPlease, reconsider your command or delete " +
			"the invalid pattern from the configuration.";

		messageInSlovakLabel = "Error message in Slovak";

		parameterDefinedNote = "Following parameter is already defined";
		ignoredNotice = "It will be ignored.";

		bigRangeIsSlowWarning = "Due to component used listing a huge " +
			"amount\nof lines will take a huge amount of time to\nprocess.";
		considerExportLabel = "Please, consider export instead.";

		wantSlowBigRangeQuestion = "Despite it, do you want to continue " +
			"listing\nthe lines in a slow way?";

		titleSeparator = " – ";
		errorTitle = "error";
		questionTitle = "question";
		exportTitle = "Export";

		lastQueryLabel = "Last query";

		selectPageLabel = "select page";
		selectRowsLabel = "select rows";
		pageLabel = "page";
		rowsLabel = "rows";

		enterPageLabel = "Enter page";
		enterRowsLabel = "Enter range of rows";

		displayedRowsLabel = "Displayed number of rows";
		totalRowsLabel = "Total number of rows";

		exportHTMLFilter = "HTML files";
		exportIOFailure = "Export failed! Received I/O error message:\n\n%s";
		exportOtherFailure = "Export failed for an unknown reason.\n" +
			"Recieved error message:\n\n%s";
		exportOk = "The file:\n%s\nhad been exported successfully.";

		extensionAutoappendWarning = "You didn’t specify the file " +
			"extension,\nso it was appended automatically, but\nthe new " +
			"file already exists.";
		overwriteQuestion = "Do you want to overwrite the file?";

		translationReadError = "Translation read error.\nRecieved error " +
			"message:\n\n%s";


		if (null != language && !"English".equals(language)) try
		{
			translate.otvorNaČítanie(language + ".lng");

			translationAuthors = updateTranslation(
				"translationAuthors", translationAuthors);
			translationDate = updateTranslation(
				"translationDate", translationDate);

			menuLabel = updateTranslation("menuLabel", menuLabel);

			clearLabel = updateTranslation("clearLabel", clearLabel);
			exportLabel = updateTranslation("exportLabel", exportLabel);
			fullscreenLabel = updateTranslation(
				"fullscreenLabel", fullscreenLabel);
			helpLabel = updateTranslation("helpLabel", helpLabel);
			exitLabel = updateTranslation("exitLabel", exitLabel);

			menuMnemo = updateMnemo("menuMnemo", menuMnemo);

			clearMnemo = updateMnemo("clearMnemo", clearMnemo);
			exportMnemo = updateMnemo("exportMnemo", exportMnemo);
			fullscreenMnemo = updateMnemo("fullscreenMnemo", fullscreenMnemo);
			helpMnemo = updateMnemo("helpMnemo", helpMnemo);
			exitMnemo = updateMnemo("exitMnemo", exitMnemo);

			warningLabel = updateTranslation("warningLabel", warningLabel);
			noteLabel = updateTranslation("noteLabel", noteLabel);

			yesLabel = updateTranslation("yesLabel", yesLabel);
			noLabel = updateTranslation("noLabel", noLabel);
			okLabel = updateTranslation("okLabel", okLabel);
			cancelLabel = updateTranslation("cancelLabel", cancelLabel);

			invalidParameterError = updateTranslation(
				"invalidParameterError", invalidParameterError);
			invalidPropertyError = updateTranslation(
				"invalidPropertyError", invalidPropertyError);
			invalidPageNumberError = updateTranslation(
				"invalidPageNumberError", invalidPageNumberError);
			invalidStartingRowError = updateTranslation(
				"invalidStartingRowError", invalidStartingRowError);
			invalidFinishingRowError = updateTranslation(
				"invalidFinishingRowError", invalidFinishingRowError);

			emptyProtocolError = updateTranslation(
				"emptyProtocolError", emptyProtocolError);
			emptyServerNameError = updateTranslation(
				"emptyServerNameError", emptyServerNameError);
			connectionNotInitializedError = updateTranslation(
				"connectionNotInitializedError", connectionNotInitializedError);
			notConnectedError = updateTranslation(
				"notConnectedError", notConnectedError);

			commandExecutionError = updateTranslation(
				"commandExecutionError", commandExecutionError);

			patternGotEmptyStringError = updateTranslation(
				"patternGotEmptyStringError", patternGotEmptyStringError);

			messageInSlovakLabel = updateTranslation(
				"messageInSlovakLabel", messageInSlovakLabel);

			parameterDefinedNote = updateTranslation(
				"parameterDefinedNote", parameterDefinedNote);
			ignoredNotice = updateTranslation("ignoredNotice", ignoredNotice);

			bigRangeIsSlowWarning = updateTranslation(
				"bigRangeIsSlowWarning", bigRangeIsSlowWarning);
			considerExportLabel = updateTranslation(
				"considerExportLabel", considerExportLabel);

			wantSlowBigRangeQuestion = updateTranslation(
				"wantSlowBigRangeQuestion", wantSlowBigRangeQuestion);

			titleSeparator = updateTranslation(
				"titleSeparator", titleSeparator);
			errorTitle = updateTranslation("errorTitle", errorTitle);
			questionTitle = updateTranslation("questionTitle", questionTitle);
			exportTitle = updateTranslation("exportTitle", exportTitle);

			lastQueryLabel = updateTranslation(
				"lastQueryLabel", lastQueryLabel);

			selectPageLabel = updateTranslation(
				"selectPageLabel", selectPageLabel);
			selectRowsLabel = updateTranslation(
				"selectRowsLabel", selectRowsLabel);
			pageLabel = updateTranslation("pageLabel", pageLabel);
			rowsLabel = updateTranslation("rowsLabel", rowsLabel);

			enterPageLabel = updateTranslation(
				"enterPageLabel", enterPageLabel);
			enterRowsLabel = updateTranslation(
				"enterRowsLabel", enterRowsLabel);

			displayedRowsLabel = updateTranslation(
				"displayedRowsLabel", displayedRowsLabel);
			totalRowsLabel = updateTranslation(
				"totalRowsLabel", totalRowsLabel);

			exportHTMLFilter = updateTranslation(
				"exportHTMLFilter", exportHTMLFilter);
			exportIOFailure = updateTranslation(
				"exportIOFailure", exportIOFailure);
			exportOtherFailure = updateTranslation(
				"exportOtherFailure", exportOtherFailure);
			exportOk = updateTranslation("exportOk", exportOk);

			extensionAutoappendWarning = updateTranslation(
				"extensionAutoappendWarning", extensionAutoappendWarning);
			overwriteQuestion = updateTranslation(
				"overwriteQuestion", overwriteQuestion);

			translationReadError = updateTranslation(
				"translationReadError", translationReadError);

			translate.close();
		}
		catch (IOException ioe)
		{
			errorMessage(String.format(translationReadError, ioe.getMessage()));
		}


		// Translate components:
		Svet.textTlačidla("áno", yesLabel);
		Svet.textTlačidla("nie", noLabel);
		Svet.textTlačidla("ok", okLabel);
		Svet.textTlačidla("zrušiť", cancelLabel);

		clearItem.text(clearLabel);
		clearItem.mnemonickaSkratka(clearMnemo);
		exportItem.text(exportLabel);
		exportItem.mnemonickaSkratka(exportMnemo);
		fullscreenItem.text(fullscreenLabel);
		fullscreenItem.mnemonickaSkratka(fullscreenMnemo);
		helpItem.text(helpLabel);
		helpItem.mnemonickaSkratka(helpMnemo);

		Svet.položkaPonukyKoniec().text(exitLabel);
		Svet.položkaPonukyKoniec().mnemonickaSkratka(exitMnemo);

		Svet.premenujPoložkuHlavnejPonuky(0, menuLabel, menuMnemo);

		// This part is not translated:
		// –Svet.premenujPoložkuHlavnejPonuky(1, languageLabel, Kláves.VK_L);—
		// —englishItem.text(englishLabel);—
		// —englishItem.mnemonickaSkratka(Kláves.VK_E);—
	}

	private void generateTranslationItem()
	{
		String newName = Svet.zadajReťazec("<html><p><b>This will generate " +
			"a .lng file for a new translation.</b></p><p><i>All texts in " +
			"the new file will be in English.</i></p><p>Please, enter new " +
			".lng file name:</p></html>", title);

		if (null == newName)
			return;

		// To restrict the “lower” variable scope (it is not safe to keep it):
		{
			String lower = newName.toLowerCase();
			if (!lower.endsWith(".lng")) newName += ".lng";
		}

		if (Súbor.jestvuje(newName))
		{
			errorMessage("Sorry, the .lng file must not exist.\nYou " +
				"might delete the old file manually,\nbut I am not " +
				"allowed to overwrite it.");
			return;
		}

		Svet.skry();
		translate(null);
		try
		{
			translate.otvorNaZápis(newName);

			translate.vymažVlastnosti();

			translate.zapíšKomentárVlastností(" Enter author names here:");
			translate.zapíšVlastnosť("translationAuthors", "");

			translate.zapíšPrázdnyRiadokVlastností();

			translate.zapíšKomentárVlastností(
				" Please, update the translation date:");
			translate.zapíšVlastnosť("translationDate", translationDate);

			translate.zapíšPrázdnyRiadokVlastností();

			translate.zapíšVlastnosť("menuLabel", menuLabel);

			translate.zapíšPrázdnyRiadokVlastností();

			translate.zapíšVlastnosť("clearLabel", clearLabel);
			translate.zapíšVlastnosť("exportLabel", exportLabel);
			translate.zapíšVlastnosť(
				"fullscreenLabel", fullscreenLabel);
			translate.zapíšVlastnosť("helpLabel", helpLabel);
			translate.zapíšVlastnosť("exitLabel", exitLabel);

			translate.zapíšPrázdnyRiadokVlastností();

			translate.zapíšVlastnosť("menuMnemo",
				("" + (char)menuMnemo).toUpperCase());

			translate.zapíšPrázdnyRiadokVlastností();

			translate.zapíšVlastnosť("clearMnemo",
				("" + (char)clearMnemo).toUpperCase());
			translate.zapíšVlastnosť("exportMnemo",
				("" + (char)exportMnemo).toUpperCase());
			translate.zapíšVlastnosť("fullscreenMnemo",
				("" + (char)fullscreenMnemo).toUpperCase());
			translate.zapíšVlastnosť("helpMnemo",
				("" + (char)helpMnemo).toUpperCase());
			translate.zapíšVlastnosť("exitMnemo",
				("" + (char)exitMnemo).toUpperCase());

			translate.zapíšPrázdnyRiadokVlastností();

			translate.zapíšVlastnosť("warningLabel", warningLabel);
			translate.zapíšVlastnosť("noteLabel", noteLabel);

			translate.zapíšPrázdnyRiadokVlastností();

			translate.zapíšVlastnosť("yesLabel", yesLabel);
			translate.zapíšVlastnosť("noLabel", noLabel);
			translate.zapíšVlastnosť("okLabel", okLabel);
			translate.zapíšVlastnosť("cancelLabel", cancelLabel);

			translate.zapíšPrázdnyRiadokVlastností();

			translate.zapíšVlastnosť(
				"invalidParameterError", invalidParameterError);
			translate.zapíšVlastnosť(
				"invalidPropertyError", invalidPropertyError);
			translate.zapíšVlastnosť(
				"invalidPageNumberError", invalidPageNumberError);
			translate.zapíšVlastnosť(
				"invalidStartingRowError", invalidStartingRowError);
			translate.zapíšVlastnosť(
				"invalidFinishingRowError", invalidFinishingRowError);

			translate.zapíšPrázdnyRiadokVlastností();

			translate.zapíšVlastnosť(
				"emptyProtocolError", emptyProtocolError);
			translate.zapíšVlastnosť(
				"emptyServerNameError", emptyServerNameError);
			translate.zapíšVlastnosť(
				"connectionNotInitializedError", connectionNotInitializedError);
			translate.zapíšVlastnosť(
				"notConnectedError", notConnectedError);

			translate.zapíšPrázdnyRiadokVlastností();

			translate.zapíšVlastnosť(
				"commandExecutionError", commandExecutionError);

			translate.zapíšPrázdnyRiadokVlastností();

			translate.zapíšVlastnosť(
				"patternGotEmptyStringError", patternGotEmptyStringError);

			translate.zapíšPrázdnyRiadokVlastností();

			translate.zapíšVlastnosť(
				"messageInSlovakLabel", messageInSlovakLabel);

			translate.zapíšPrázdnyRiadokVlastností();

			translate.zapíšVlastnosť(
				"parameterDefinedNote", parameterDefinedNote);
			translate.zapíšVlastnosť("ignoredNotice", ignoredNotice);

			translate.zapíšPrázdnyRiadokVlastností();

			translate.zapíšVlastnosť(
				"bigRangeIsSlowWarning", bigRangeIsSlowWarning);
			translate.zapíšVlastnosť(
				"considerExportLabel", considerExportLabel);

			translate.zapíšPrázdnyRiadokVlastností();

			translate.zapíšVlastnosť(
				"wantSlowBigRangeQuestion", wantSlowBigRangeQuestion);

			translate.zapíšPrázdnyRiadokVlastností();

			translate.zapíšVlastnosť("titleSeparator", titleSeparator);
			translate.zapíšVlastnosť("errorTitle", errorTitle);
			translate.zapíšVlastnosť("questionTitle", questionTitle);
			translate.zapíšVlastnosť("exportTitle", exportTitle);

			translate.zapíšPrázdnyRiadokVlastností();

			translate.zapíšVlastnosť("lastQueryLabel", lastQueryLabel);

			translate.zapíšPrázdnyRiadokVlastností();

			translate.zapíšVlastnosť("selectPageLabel", selectPageLabel);
			translate.zapíšVlastnosť("selectRowsLabel", selectRowsLabel);
			translate.zapíšVlastnosť("pageLabel", pageLabel);
			translate.zapíšVlastnosť("rowsLabel", rowsLabel);

			translate.zapíšPrázdnyRiadokVlastností();

			translate.zapíšVlastnosť("enterPageLabel", enterPageLabel);
			translate.zapíšVlastnosť("enterRowsLabel", enterRowsLabel);

			translate.zapíšPrázdnyRiadokVlastností();

			translate.zapíšVlastnosť("displayedRowsLabel", displayedRowsLabel);
			translate.zapíšVlastnosť("totalRowsLabel", totalRowsLabel);

			translate.zapíšPrázdnyRiadokVlastností();

			translate.zapíšVlastnosť("exportHTMLFilter", exportHTMLFilter);
			translate.zapíšVlastnosť("exportIOFailure", exportIOFailure);
			translate.zapíšVlastnosť("exportOtherFailure", exportOtherFailure);
			translate.zapíšVlastnosť("exportOk", exportOk);

			translate.zapíšPrázdnyRiadokVlastností();

			translate.zapíšVlastnosť(
				"extensionAutoappendWarning", extensionAutoappendWarning);
			translate.zapíšVlastnosť("overwriteQuestion", overwriteQuestion);

			translate.zapíšPrázdnyRiadokVlastností();

			translate.zapíšVlastnosť(
				"translationReadError", translationReadError);

			translate.close();

			message(String.format("The file:\n%s\nhad been created " +
				"successfully.", newName));
		}
		catch (IOException ioe)
		{
			errorMessage(String.format("The creation of the .lng " +
				"file failed.\nRecieved error message:\n\n%s:\n\n%s",
				ioe.getMessage()));
		}
		finally
		{
			translate(language);
			Svet.zobraz();
		}
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
		Svet.použiKonfiguráciu("JDBCQuerier.cfg");
		Svet.skry(); Svet.nekresli();
		Súbor.predvolenáCestaDialógov(".");

		try { new JDBCQuerier(); }
		catch (Throwable t) { t.printStackTrace(); }
		finally { Svet.zobraz(); }
	}
}
