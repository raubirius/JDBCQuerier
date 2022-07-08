
// https://www.demo2s.com/java/java-swing-jtable-table-component.html

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

@SuppressWarnings("serial")
public class JTableDemo extends JPanel
{
	public JTableDemo()
	{
		try
		{
			SwingUtilities.invokeAndWait(new Runnable()
				{ public void run() { makeGUI(); }});
		}
		catch (Exception exc)
		{
			System.out.println("Can't create because of " + exc);
		}
	}

	private void makeGUI()
	{
		// Initialize column headings.
		String[] colHeads = { "Name", "Extension", "ID#" };

		// Initialize data.
		Object[][] data = {
			{ "A", "4567", "123" },
			{ "B", "7890", "234" },
			{ "C", "5543", "345" },
			{ "D", "7890", "456" },
			{ "E", "1237", "678" },
			{ "F", "5656", "654" },
			{ "G", "5623", "432" },
			{ "H", "2323", "654" },
			{ "I", "9045", "765" },
			{ "J", "4545", "876" },
			{ "K", "5459", "987" },
			{ "L", "9450", "098" },
			{ "M", "6451", "145" }};

		// Create the table.
		JTable table = new JTable(data, colHeads);

		// Add the table to a scroll pane.
		JScrollPane jsp = new JScrollPane(table);

		// Add the scroll pane to the content pane.
		add(jsp);
	}

	public static void main(String[] args)
	{
		JFrame frame = new JFrame();

		frame.add(new JTableDemo());
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.pack();
		frame.setVisible(true);
	}
}
