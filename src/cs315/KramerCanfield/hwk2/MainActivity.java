package cs315.KramerCanfield.hwk2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;
import cs315.yourname.hwk2.R;

/**
 * A starter template for an Android scan convertion mini-painter
 * 
 * @author Joel, adapted from code by Dave Akers
 * @version Fall 2013
 */
public class MainActivity extends Activity implements OnItemSelectedListener
{
	private static final String TAG = "MiniPaint"; //for logging/debugging
	
	private MiniPaintView paintView; //the view we'll be drawing on (for later reference). Cast the variable to make more specific

	private Spinner modeSpinner; //a spinner for choosing mode
	private Spinner colorSpinner; //a spinner for choosing mode
	
	//for UI/spinners/etc
	private HashMap<String,Integer> colorsByName;
	private HashMap<String,Integer> fileResources; //for opening minipaint files
	private int fileResId;
	
	
	/**
	 * Called when the activity is started
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main); //use the activity_main layout
		
		paintView = (MiniPaintView)this.findViewById(R.id.paint_view); //get access to view for later
		
		//Set up mode spinner
		modeSpinner = (Spinner) findViewById(R.id.draw_mode_spinner);
		ArrayAdapter<CharSequence> modeAdapter = ArrayAdapter.createFromResource(this,R.array.draw_mode_array, android.R.layout.simple_spinner_item);
		modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		modeSpinner.setAdapter(modeAdapter);
		modeSpinner.setOnItemSelectedListener(this);
		
		colorsByName = new HashMap<String,Integer>();
		colorsByName.put("White", Color.WHITE);
		colorsByName.put("Black", Color.BLACK);
		colorsByName.put("Red", Color.RED);
		colorsByName.put("Green", Color.GREEN);
		colorsByName.put("Blue", Color.BLUE);
		//can add new colors here! Make sure to also add them to the adapter's list below

		//Set up color spinner
		colorSpinner = (Spinner)findViewById(R.id.color_spinner);
		ArrayAdapter<String> colorAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item,
				new String[] {"White","Black", "Red", "Green", "Blue"});
		colorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		colorSpinner.setAdapter(colorAdapter);
		colorSpinner.setOnItemSelectedListener(this);
		
		//for loading minipaint files
		fileResources = new HashMap<String,Integer>();
		fileResources.put("test1", R.raw.test1);
		fileResources.put("hi", R.raw.hi);
		fileResources.put("flower", R.raw.flower);
		fileResources.put("house", R.raw.house);		
		fileResId = -1;
	}

	/**
	 * If the clear button is pressed
	 */
	public void clearButton(View view)
	{
		paintView.clearDrawing();
	}
	
	/**
	 * If the file button is pressed
	 */
	public void fileButton(View view)
	{
		final String[] keys = fileResources.keySet().toArray(new String[0]); //get list of the keys
		AlertDialog.Builder builder = new AlertDialog.Builder(this); //make a popup
	    builder.setTitle("Select Input File");
	    builder.setItems(keys,new DialogInterface.OnClickListener() {
	               public void onClick(DialogInterface dialog, int which) {
	            	   fileResId = fileResources.get(keys[which]);	            	   
	            	   dialog.dismiss();
	               }
	    });
	    AlertDialog dialog = builder.create(); //this is messy, but does the job. Bonus points if you clean it up!
	    dialog.setOnDismissListener(new OnDismissListener() { //when popup is closed, do the parsing!
			public void onDismiss(DialogInterface dialog){ parse(fileResId); }
	    });
	    dialog.show();
	}
	
	/**
	 * For when an item is chosen from a spinner
	 */
	public void onItemSelected(AdapterView<?> parent, View view, int pos, long id)
	{
		if(parent == modeSpinner)
		{
			String item = (String)parent.getItemAtPosition(pos);
			paintView.setMode(pos); //currently pos and mode need to match; otherwise use a big if statement on the above value
		}
		else if(parent == colorSpinner)
		{
			String item = (String)parent.getItemAtPosition(pos);
			int color = colorsByName.get(item);
			paintView.setColor(color);
		}
	}

	//don't have an empty option in the spinners, so don't need this method
	public void onNothingSelected(AdapterView<?> parent){}
	
	
	/**
	 * Auto-generated; provides menu.
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	/**
	 * Provided method that parses a .minipaint file (a set of drawing commands)
	 * @param resource the Android resource id of the file.
	 */
	public void parse(int resource)
	{
		Log.d(TAG,"Parsing "+resource);
		if(resource == -1) //if undefined, return
			return;
		
		InputStream inputStream = getResources().openRawResource(resource);
		BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
		
		try
		{
			String line = in.readLine();
			while(line != null) //read line by line
			{
				Log.d(TAG,line);
				String[] args = line.split(" ");
				if(args[0].equals("COLOR")){
					if(args[1].equals("black"))
						paintView.setColor(Color.BLACK);
					else if(args[1].equals("white"))
						paintView.setColor(Color.WHITE);
					else if(args[1].equals("red"))
						paintView.setColor(Color.RED);
					else if(args[1].equals("green"))
						paintView.setColor(Color.GREEN);
					else if(args[1].equals("blue"))
						paintView.setColor(Color.BLUE);
				}
				else if(args[0].equals("P")){
					paintView.drawPoint(Integer.parseInt(args[1]), Integer.parseInt(args[2]));
				}
				else if(args[0].equals("L")){
					paintView.drawLine(
							Integer.parseInt(args[1]), 
							Integer.parseInt(args[2]),
							Integer.parseInt(args[3]),
							Integer.parseInt(args[4]));
				}
				else if(args[0].equals("C")){
					paintView.drawCircle(
							Integer.parseInt(args[1]), 
							Integer.parseInt(args[2]),
							Integer.parseInt(args[3]));
				}
				else if(args[0].equals("Y")){
					int[] coords = new int[args.length-1]; //store the coords to avoid duplicate parsing
					for(int i=1; i<args.length; i++)
						coords[i-1] = Integer.parseInt(args[i]);
					
					for(int i=0; i<coords.length-3; i+=2) //draw line to every two coords
						paintView.drawLine(coords[i], coords[i+1], coords[i+2], coords[i+3]);
				}
				else if(args[0].equals("END")){
					break; //exit the loop
				}
				else if(args[0].equals("R")){
					paintView.drawRectangle(
							Integer.parseInt(args[1]), 
							Integer.parseInt(args[2]),
							Integer.parseInt(args[3]),
							Integer.parseInt(args[4]));
				}
				else if(args[0].equals("F")){
					paintView.floodFill(Integer.parseInt(args[1]), Integer.parseInt(args[2]));
				}
				else if(args[0].equals("A")){
					paintView.airBrush(Integer.parseInt(args[1]), Integer.parseInt(args[2]));
				}
				else {
					Log.d(TAG,"Unhandled command: "+line);
				}

				line = in.readLine(); //grab the next line!
			}
		}
		catch(IOException ioe) //in case something goes wrong
		{
			Toast toast = Toast.makeText(getApplicationContext(), "Error parsing file: "+ioe.toString(), Toast.LENGTH_SHORT);
			toast.show();
			Log.d(TAG,ioe.toString());
		}
	}
}
