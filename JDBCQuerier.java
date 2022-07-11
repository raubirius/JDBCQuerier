
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
		body = new StringBuffer()/*, table = new StringBuffer()*/,
		exportBuffer = new StringBuffer();

	private StringBuffer buffer = body;

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

	private final PoložkaPonuky clearItem;
	private final PoložkaPonuky exportItem;
	private final PoložkaPonuky fullscreenItem;
	private final PoložkaPonuky helpItem;

	private boolean fullscreen = false;

	private int pageRows = 25;

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
	private final Pattern pageCommand = new Pattern("^\\s*list\\s+page\\s+" +
		"([0-9]+)\\s*;?\\s*$", "$1");
	private final Pattern rowsCommand = new Pattern("^\\s*list\\s+rows\\s+" +
		"([0-9]+)[\\s,]+([0-9]+)\\s*;?\\s*$", "$1 $2");
	private final Pattern exportCommand =
		new Pattern("^\\s*export\\s*;?\\s*$", null);

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

		// translate("English"); // TODO: select through menu‼
		translate("Slovak");

		new ObsluhaUdalostí()
		{
			// @Override public void čítajKonfiguráciu(Súbor súbor)
			// 	throws IOException { loadConfig(súbor); }

			@Override public void potvrdenieÚdajov()
			{ doQuery(Svet.prevezmiReťazec()); }

			@Override public void ukončenie() { disconnect(); }

			@Override public void klávesováSkratka() { onCommand(); }

			@Override public void aktiváciaOdkazu()
			{ openURL(ÚdajeUdalostí.poslednýOdkaz()); }
		};

		try {
			Súbor súbor = new Súbor();
			súbor.otvorNaČítanie("config.cfg");
			loadConfig(súbor);
			súbor.zavri();
			súbor = null;
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}

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

	private void error(SQLException t)
	{
		boolean joinFlag;
		if (!splitFlag)
		{
			splitHtml();
			joinFlag = true;
		}
		else joinFlag = false;

		// t.printStackTrace();
		body.append("\n<p class=\"error\">");
		body.append(replaceHTMLEntities(t.getMessage()).replace("\n", "<br>"));
		body.append("</p>\n");
		/*
		 * I wanted to write the whole exception here (for any case). The
		 * content should’ve been “hideable,” but the hidden style
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
		if (!Svet.celáObrazovka(fullscreen = !fullscreen)) Svet.pípni();
	}


	private void loadConfig(Súbor súbor) throws IOException
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
		// sendStringParametersAsUnicode = null;
		instance = null;

		connectionURL = null;

		user = null;
		password = null;
		domain = null;

		Zoznam<String> vlastnosti = súbor.zoznamVlastností();

		/**
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
			// sendStringParametersAsUnicode = súbor.čítajVlastnosť(
			// 	"sendStringParametersAsUnicode", sendStringParametersAsUnicode);
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
				// System.out.println("parameter: " + skrátená);

				if (skrátená.isEmpty() || skrátená.equals("name") ||
					skrátená.equals("protocol") || skrátená.equals("port")
					// || skrátená.equals("sendStringParametersAsUnicode")
					)
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
					System.err.println(warningLabel + ": " +
						invalidPropertyError + ": " + skrátená + "\n" +
						ignoredNotice);
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

		// table.setLength(0);
		/*table*/buffer.append("<table border=\"0\" cellpadding=\"0\" " +
			"cellspacing=\"0\" class=\"frame\"><tr><td><table " +
			"border=\"0\" cellpadding=\"2\" cellspacing=\"1\" " +
			"class=\"result\">\n");

		if (null != queryHead)
		{
			/*table*/buffer.append("<tr>\n\t<th>&#8288;</th>");

			int length = queryHead.length;
			for (int j = 0; j < length; ++j)
			{
				/*table*/buffer.append("<th>");
				/*table*/buffer.append(replaceHTMLEntities(queryHead[j]));
				/*table*/buffer.append("</th>");
			}

			/*table*/buffer.append("\n</tr>");
		}

		lastDisplayedCount = 0;

		for (int i = start; i < finish; ++i)
		{
			/*table*/buffer.append("\n<tr>\n\t");

			String[] row = queryData.get(i);
			if (null == row) continue;

			++lastDisplayedCount;
			/*table*/buffer.append("<td>");
			/*table*/buffer.append(i + 1);
			/*table*/buffer.append(".</td>");

			int length = row.length;
			for (int j = 0; j < length; ++j)
			{
				String content = row[j];

				/*table*/buffer.append("<td>");
				if (null == content || content.isEmpty())
					/*table*/buffer.append("&#8288;");
					// /*table*/buffer.append(" ");
				else
					/*table*/buffer.append(replaceHTMLEntities(content));
				/*table*/buffer.append("</td>");
			}

			/*table*/buffer.append("\n</tr>");
		}

		/*table*/buffer.append("\n</table></td></tr></table>");
	}

	private void doQuery(String query)
	{
		// TODO: help for custom commands: help; clear; list page n;
		// list rows m, n; export;

		// splitHtml();
		// System.out.println("sb2: " + body);

		if (!isConnected())
		{
			error(notConnectedError);
			// joinHtml();
			return;
		}

		if (helpCommand.matches(query))
		{
			help();
			return;
		}

		{
			String match = pageCommand.match(query);
			if (null != match)
			{
				listPage(match);
				return;
			}

			Matcher matcher = rowsCommand.matcher(query);
			if (matcher.matches())
			{
				listRows(matcher);
				return;
			}
		}

		if (exportCommand.matches(query))
		{
			// TODO: allow to enter a file name and the overwrite flag.
			export();
			return;
		}

		// System.out.println("Query: " + query);

		for (Pattern pattern : patterns)
		{
			String match = pattern.match(query);
			// System.out.println("Pattern: " + pattern.pattern +
			// 	" match " + (null != match));
			if (null != match)
			{
				if (match.isEmpty())
				{
					error(patternGotEmptyStringError);
					return;
				}
				query = match;
				// System.out.println("New query: " + query);
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
			{
				for (int i = 1; i <= numberOfColumns; ++i)
				{
					queryHead[i - 1] = rsmd.getColumnName(i);
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

			listPage(1);
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
		else if (export == command) export();
		else if (fullscr == command) fullscreen();
		else if (help == command) help();
	}

	public void listPage(int page)
	{
		// System.out.println("page: " + page);
		clear();
		splitHtml();
		generateLastQuery();

		generateControls(page);
		generateTable((page - 1) * pageRows, page * pageRows);
		// System.out.println("generate: " + ((page - 1) * pageRows) +
		// 	" – " + (page * pageRows));
		// body.append(table);
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
		{
			int page = newPage.intValue();
			int size = queryData.size();
			if (page < 1) page = 1;
			else if (page > size) page = size;
			listPage(page);
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
			// body.append(table);
			generateControls(start, finish);

			generateSummary();
			joinHtml();
		}
	}

	public void listRows(Matcher matcher)
	{
		String starting = matcher.group(1);
		String finishing = matcher.group(2);

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
		// System.out.println("url: " + url);
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

	private void export()
	{
		String fileName = Súbor.dialógUložiť(exportTitle, null,
			exportHTMLFilter + " (*.htm; *.html)");

		if (null != fileName)
		{
			String lowerName = fileName.toLowerCase();
			if (!lowerName.endsWith(".htm") && !lowerName.endsWith(".html"))
			{
				fileName += ".html";
				if (Súbor.jestvuje(fileName) && !question("<html><b>" +
					warningLabel + "!</b><br> <br>" +
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

				// generateLastQuery();
				if (null != lastQuery)
				{
					buffer.append("<p class=\"last_query\">");
					buffer.append(replaceHTMLEntities(lastQuery));
					buffer.append("</p>");
				}

				generateTable(0, queryData.size());
				// generateSummary();

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

				// clear();
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
		message("Help is not implemented, yet, but you can\n" +
			"use the following commands: clear; list page #;\n" +
			"list rows #, #; and export.");
	}


	// The translations are initialized in the translate method (below):

	private static String menuLabel;

	private static String clearLabel;
	private static String exportLabel;
	private static String fullscreenLabel;
	private static String helpLabel;
	private static String exitLabel;

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


	public void translate(String language)
	{
		// Default values (missing translations will stay in English):
		menuLabel = "Menu";

		clearLabel = "Clear console";
		exportLabel = "Export data…";
		fullscreenLabel = "Full screen";
		helpLabel = "Help…";
		exitLabel = "Exit";

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


		if (!"English".equals(language)) try
		{
			translate.otvorNaČítanie(language + ".lng");

			menuLabel = updateTranslation("menuLabel", menuLabel);

			clearLabel = updateTranslation("clearLabel", clearLabel);
			exportLabel = updateTranslation("exportLabel", exportLabel);
			fullscreenLabel = updateTranslation(
				"fullscreenLabel", fullscreenLabel);
			helpLabel = updateTranslation("helpLabel", helpLabel);
			exitLabel = updateTranslation("exitLabel", exitLabel);

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
		clearItem.mnemonickaSkratka(Kláves.VK_C); // TODO read.
		exportItem.text(exportLabel);
		exportItem.mnemonickaSkratka(Kláves.VK_E);
		fullscreenItem.text(fullscreenLabel);
		fullscreenItem.mnemonickaSkratka(Kláves.VK_L);
		helpItem.text(helpLabel);
		helpItem.mnemonickaSkratka(Kláves.VK_H);

		Svet.položkaPonukyKoniec().text(exitLabel);
		Svet.položkaPonukyKoniec().mnemonickaSkratka(Kláves.VK_X);

		Svet.premenujPoložkuHlavnejPonuky(0, menuLabel, Kláves.VK_M);
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

		try { new JDBCQuerier(); }
		catch (Throwable t) { t.printStackTrace(); }
		finally { Svet.zobraz(); }
	}
}
