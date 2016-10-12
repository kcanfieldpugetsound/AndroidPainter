package cs315.KramerCanfield.hwk2;

import java.util.Stack;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Point;
import android.util.AttributeSet;
import android.util.Log;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * A starter template for an Android scan conversion mini-painter. Includes the logic for doing the scan conversions
 * 
 * This class provides a dedicated thread for drawing, though the actual work is still in the UI thread,
 * because it is user driven.
 * 
 * @author Joel, adapted from code by Dave Akers
 * @author Kramer Canfield
 * @version Fall 2013
 * @version 24 September 2013
 */
public class MiniPaintView extends SurfaceView implements SurfaceHolder.Callback
{
	private static final String TAG = "MiniPaintView";

	public static final int POINT_MODE = 0; //mode number (supposedly faster than enums)
	public static final int LINE_MODE = 1;
	public static final int CIRCLE_MODE = 2;
	public static final int POLYLINE_MODE = 3;
	public static final int RECTANGLE_MODE = 4;
	public static final int FLOOD_FILL_MODE = 5;
	public static final int AIRBRUSH_MODE = 6;

	private static final int PIXEL_SIZE = 2; //how "big" to make each pixel; change this for debugging
	private boolean DELAY_MODE = false; //whether to show a delay on the drawing (for debugging)
	private static final int DELAY_TIME = 5; //how long to pause between pixels; delay in ms; 5 is short, 50 is long

	private SurfaceHolder _holder; //basic drawing structure
	private DrawingThread _thread;
	private Context _context;
	
	private Bitmap _bmp; //frame buffer
	private int _width; //size of the image buffer
	private int _height;
	private Matrix _scaleM; //scale based on pixel size

	private int _mode; //drawing mode
	private int _color; //current painting color
	
	private int _startX; //starting points for multi-click operations
	private int _startY;
	
	public static final int AIRBRUSH_WIDTH = 30;//set the width (diameter) of the airbrush, when the width is 30, the radius is 15
	
	
	
	
	/**
	 * Respond to touch events
	 */
	@Override
	public boolean onTouchEvent(MotionEvent event)
	{
		int x = (int)event.getX()/PIXEL_SIZE; //scale event to the size of the frame buffer!
		int y = (int)event.getY()/PIXEL_SIZE;

		switch(_mode) {
		case POINT_MODE:
			drawPoint(x,y);
			break;
		case LINE_MODE: 
			if(_startX < 0) { //see if we have a "first click" set of coords
				_startX = x; 
				_startY = y;
			}
			else {
				drawLine(_startX, _startY, x, y);
				_startX = -1;
			}
			break;
		case CIRCLE_MODE:
			if(_startX < 0) { //see if we have a "first click" set of coords
				_startX = x; 
				_startY = y;
			}
			else {
				int radius = (int) Math.sqrt((x - _startX)*(x - _startX) + (y - _startY)*(y - _startY));
				drawCircle(_startX, _startY, radius);
				_startX = -1;
			}
			break;
		case POLYLINE_MODE: 
			if(_startX < 0) { //see if we have a "first click" set of coords
				_startX = x; 
				_startY = y;
			}
			else {
				drawLine(_startX, _startY, x, y);
				_startX = x;//update the start coordinate to be the last used point on the screen rather than resetting it to a pre-defined number, this is what allows for multiple lines
				_startY = y;
			}
			
			break;
		case RECTANGLE_MODE:
			if(_startX < 0) { //see if we have a "first click" set of coords
				_startX = x; 
				_startY = y;
			}
			else {
				drawRectangle(_startX, _startY, x, y);
				_startX = -1;
			}

			break;
		case FLOOD_FILL_MODE://it was explained to me by someone else that flood fill and airbrush only require one "click" to start, so we don't need the check like the other methods for the "first click" coordinates
			floodFill(x,y);
			
			break;
		case AIRBRUSH_MODE:
			airBrush(x, y);
			break;
		}

		return super.onTouchEvent(event); //pass up the tree, as needed
	}

	/**
	 * Draws a single point on the screen in the current paint color
	 * @param x x-coord of the point
	 * @param y y-coord of the point
	 */
	public void drawPoint(int x, int y)
	{
		setPixel(x, y); //I've done this one for you. You're welcome ;) //Thanks.
	}

	/**
	 * Draws a line on the screen in the current paint color
	 * @param startX x-coord of starting point
	 * @param startY y-coord of starting point
	 * @param endX x-coord of ending point
	 * @param endY y-coord of ending point
	 */
	public void drawLine(int startX, int startY, int endX, int endY)
	{
		//ALGORITHM: Bresenham's algorithm for drawing lines is based on finding the pixels that are as close as possible to the theoretical perfect line
		//we choose to go up or down (or left or right depending on direction) based on the previous decision. We can almost think of this like a cascade of error piling up over time.
		//the pixel to choose is based on a mathematical derivation based on choosing which pixel is closer to the theoretical line.
		//From there, we can use mathematical induction to find an equation for each current decision based on the previous decision.
		
		/////////////////
		//SETUP
		/////////////////
		
		//make local variables for the left and right end points
		int x0;//the left
		int y0;
		int x1;//the right
		int y1;
		
		//check to see which point is the left point, then store them appropriately
		if(startX<endX)//if the startX is on the left, store that point as (x0 , y0)
		{
			x0 = startX;
			y0 = startY;
			x1 = endX;
			y1 = endY;
		}
		else
		{
			x0 = endX;
			y0 = endY;
			x1 = startX;
			y1 = startY;
		}
		_bmp.setPixel(x0, y0, _color);//set the first pixel to be the color
		
		
		//we need to figure out if y is decreasing to handle half of the cases
		boolean yDecreasing;
		
		if(y1<y0)
		{
			yDecreasing = true;
		}
		else
		{
			yDecreasing = false;
		}
		
//		Calculate constants dx, dy, 2dy (called dy2 for variable name) and 2dy - 2dx(called dy2_2dx)
		int dx = x1-x0;
		int dx2 = 2*dx;//store this to decrease math done per pixel
		
		int dy;//determine dy based on whether or not the line has negative slope
		if(!yDecreasing)
			dy = y1-y0;
		else
			dy = y0-y1;
		
		int dy2= (2*dy);
		int dy2_2dx = dy2-(2*dx);
		int dx2_2dy = dx2-dy2;
		
		int previousY=y0;
		int currentY=y0;
		
		int previousX=x0;
		int currentX=x0;
		
//		Calculate starting value of decision parameter
		//pK is a decision parameter used to help make the next decision pKplusOne
		int p0 = dy2-dx;//the initial decision which is not actually a decision because we want to color the start pixel for sure
		int previousPK=p0;
		int pKplusOne;//the next decision
		
	
		//k is just a counter to tell us how many decisions we've made or how many pixels we've colored
		
		/////////////////
		//THE ACTUAL LINE MAKING AND DRAWING
		/////////////////

		
		if(yDecreasing==false)//if the line's y values are "increasing" on the screen, actually decreasing
		{
			//depending on if dy>dx, make separate cases and change iteration variable to dy or dx
			if(dy>dx)//for the steep case
			{
				for(int k=1; k<dy; k++)
				{
					_bmp.setPixel(currentX, y0+k, _color);
					//based on our previous decision, make a new decision and increment x as necessary
					if(previousPK<0)
					{
						pKplusOne=previousPK + dx2;//the equations from lecture
						currentX= previousX;
					}
					else
					{
						pKplusOne=previousPK + dx2_2dy;//the equations from lecture
						currentX= previousX + 1;
					}
					previousX = currentX;//set the previous to be the current so we can use the current as the previous for the next time
					_bmp.setPixel(currentX, y0+k, _color);
					previousPK = pKplusOne;
				}
			}
			else//for the not-steep case
			{
				for(int k=1; k<dx; k++)//going along each x from left to right
				{
					_bmp.setPixel(x0+k, currentY, _color);
					//based on our previous decision, make a new decision and increment y as necessary
					if(previousPK<0)
					{
						pKplusOne=previousPK + dy2;//the equations from lecture
						currentY= previousY;
					}
					else
					{
						pKplusOne=previousPK + dy2_2dx;//the equations from lecture
						currentY= previousY + 1;
					}
					previousY = currentY;//set the previous to be the current so we can use the current as the previous for the next time
					_bmp.setPixel(x0+k, currentY, _color);
					previousPK = pKplusOne;
				}
			}
		}
		else//y is decreasing
		{
			//depending on if dy>dx, make separate cases and change iteration variable to dy or dx
			if(dy>dx)//the steep case
			{
				for(int k=1; k<dy; k++)
				{
					_bmp.setPixel(currentX, y0-k, _color);//set the current pixel
					//based on our previous decision, make a new decision and increment x as necessary
					if(previousPK<0)
					{
						pKplusOne=previousPK + dx2;
						currentX= previousX;
					}
					else
					{
						pKplusOne=previousPK + dx2_2dy;
						currentX= previousX + 1;
					}
					previousX = currentX;//set the previous to be the current so we can use the current as the previous for the next time
					_bmp.setPixel(currentX, y0-k, _color);
					previousPK = pKplusOne;
				}
			}
			else//the not steep case
			{
				for(int k=1; k<dx; k++)//going along each x from left to right
				{
					_bmp.setPixel(x0+k, currentY, _color);
					//based on our previous decision, make a new decision and increment y as necessary
					if(previousPK<0)
					{
						pKplusOne=previousPK + dy2;
						currentY= previousY;
					}
					else
					{
						pKplusOne=previousPK + dy2_2dx;
						currentY= previousY - 1;
					}
					previousY = currentY;//set the previous to be the current so we can use the current as the previous for the next time
					_bmp.setPixel(x0+k, currentY, _color);
					previousPK = pKplusOne;
				}
			}
		}
		
	}

	/**
	 * Draws a circle on the screen in the current paint color
	 * @param x0 x-coord of circle center
	 * @param y0 y-coord of circle center
	 * @param radius radius of the circle
	 */
	public void drawCircle(int x0, int y0, int radius)
	{
		int x = radius;//the radius of the circle
		int p0 = 3 - (2*radius);//starting decision variable, equation from lecture and derived by the algorithm
		int pK = p0;//start with the decision to make as the first decision
		
		
		/*
		 * ALGORITHM: (for the first eighth of the circle) for each pixel (as the y value increases), until we get to the point on the line y=x,
		 * we want to see if we need to stay at the current x value, or move to the left so that the center of the pixel we choose is closer to
		 * the corresponding point on the theoretical circle.
		 */
		for(int y=0; y<x; y++)//as we increment y
		{
			//based on our previous decision, make a new decision
			if(pK>0)
			{
				pK = pK + 4*(y-x) + 10;//update pK
				x--;//go to the left
			}
			else
			{
				pK = pK + 4*y + 6;//update pK
				//stay at the same x value
				
			}
			//have one setPixel call for each pixel that we're drawing as part of each eighth of the circle
			//because of the equation of a circle, radius*radius = x*x + y*y, the drawing algorithm will still hold for all combinations of x,y with positive or negative values and for when x and y are reversed
			setPixel(x+x0, y+y0);//we have to add the starting point x0,y0 because those values represent the offset, add them so the circle can be where we clicked, not the corner of the screen
			setPixel(-x+x0, y+y0);
			setPixel(-x+x0, -y+y0);
			setPixel(x+x0, -y+y0);
			setPixel(y+x0, x+y0);
			setPixel(y+x0, -x+y0);
			setPixel(-y+x0, -x+y0);
			setPixel(-y+x0, x+y0);
		}

	}

	/**
	 * Draws a rectangle on the screen in the current paint color
	 * @param startX x-coord of first corner (i.e., upper left)
	 * @param startY y-coord of first corner
	 * @param endX x-coord of second corner (i.e., lower right)
	 * @param endY y-coord of second corner
	 */
	public void drawRectangle(int startX, int startY, int endX, int endY)
	{ 
		// JOEL SAYS: Note that you can reuse your drawLine() method, but you shouldn't reuse the floodFill() method!
		
		/*
		 * SETUP
		 */
		
		int x0;//the starting x
		int x1;//the ending x
		int y0;//the starting y
		int y1;//the ending y
		
		//we need to assign these values based on where the rectangle needs to be not which point the user clicked first,
		//in other words, draw the rectangles that are defined top-bottom and bottom-top
		if(endY>startY)
		{
			x0 = startX;
			y0 = startY;
			x1 = endX;
			y1 = endY;
		}
		else//in the case where the endY is "above" startY on the screen, change the parameters so it still draws
		{
			x0 = endX;
			y0 = endY;
			x1 = startX;
			y1 = startY;
		}
		
		/*
		 * DRAWING
		 * draw a bunch of horizontal lines very close to each other from the start to the end so it looks like a rectangle
		 */
		for(int i=0; i<(y1-y0); i++)
		{
			drawLine(x0, y0+i, x1, y0+i);
		}
		
	}

	/**
	 * Flood-fills a space on the canvas in the current paint color
	 * @param x x-coord to start filling from
	 * @param y y-coord to start filling from
	 */
	public void floodFill(int x, int y)
	{
		//ALGORITHM: This is the RUN-BASED flood fill algorithm.
					//(while the stack still has more "seeds" in it)
					//When we find a new row above or below the current pixel, add it to the stack, raise a marking flag, and keep going.
					//if there's a flag raised and we find a pixel above or below us, don't raise a flag because it's not the start of a new row
					//if a flag is raised and there's not a pixel to replace above or below us, lower the flag.
					//Run this while the stack is 
		/*
		 * SETUP
		 */
		//use a stack to store the "seed" points that are the start of a new run
		
		Stack<Point> theStack = new Stack<Point>();//the stack of points that are the "seeds," or the points to mark a location to start a new row
		
		theStack.push(new Point(x,y));//push the given "seed" onto the stack
		
		//these are the "flags" to tell us whether or not we already know there's a row above or below us
		boolean flagTopIsRaised = false;
		boolean flagBottomIsRaised = false;
		
		int oldColor = getPixel(x, y);//the color we're replacing, used to check if we need to keep going in the row
		
		//track the start and end of a row, this helps with iterating through each pixel on a line
		int xMax = x;//initialize to start, even though these will change
		int xMin = x;
		
		
		/*
		 * DRAWING
		 */
		//while there are more "seed" points to deal with
		while(!theStack.isEmpty())
		{
			//reset the flags to avoid problems
			flagTopIsRaised = false;
			flagBottomIsRaised = false;
			
			Point p=theStack.pop();//get the top "seed" off of theStack so we can work with it
			x = p.x;//store the data so we don't have to keep accessing the point's instance variables
			y = p.y;
			
			xMax = x;//initialize to start, even though these will change
			xMin = x;
			
			for(int i=0; getPixel(x+i, y) == oldColor; i++)//from the seed follow to the right as long as the next pixel is the old color
			{
				setPixel(x+i, y);//color as we go
				xMax = x+i;//update xMax
			}
			//Log.d(TAG, "x:" +x+ " y: "+y+"color:" +getPixel(x,y) );
			for(int j=1; getPixel(x-j, y) == oldColor; j++)//from the seed follow to the left as long as the next pixel is the old color
			{
				//Log.d("IN LOOP", "in the left loop");
				setPixel(x-j, y);//color as we go
				xMin = x-j;
			}
			//now we should have the locations of the left-most and right-most pixels stored as xMin and xMax for the given y value
			
			for(int k=0; k<(xMax-xMin); k++)
			{
				//for pixels and runs ABOVE
				if(getPixel(xMin + k, (y-1))==oldColor && flagTopIsRaised==false)//check to see if the pixel above marks the start of a new run (upper pixel has oldColor and the flag is not raised)
				{
					theStack.push(new Point(xMin + k, (y-1)));//add the start of the new run to the stack
					flagTopIsRaised = true;//raise the flag
				}
				if(getPixel(xMin + k, (y-1)) != oldColor)//we need to lower the flag because that run is done
				{
					flagTopIsRaised = false;//lower the flag
				}
				
				//for pixels and runs BELOW
				if(getPixel(xMin + k, (y+1))==oldColor && flagBottomIsRaised==false)//check to see if the pixel above marks the start of a new run (upper pixel has oldColor and the flag is not raised)
				{
					theStack.push(new Point(xMin + k, (y+1)));//add the start of the new run to the stack
					flagBottomIsRaised = true;//raise the flag
				}
				if(getPixel(xMin + k, (y+1)) != oldColor)//we need to lower the flag because that run is done
				{
					flagBottomIsRaised = false;//lower the flag
				}
			}
		}
		
		
	}

	/**
	 * Draws an airbrushed blob in the current paint color (blending with existing colors)
	 * @param x x-coord to center the airbrush
	 * @param y y-coord to center the airbrush
	 */
	public void airBrush(int x, int y)
	{
		//depending on how far the pixel is from the brush center, it will have a different amount of apparent transparency, more background shows for points that are farther from the brush center
		//calculate  a percentage of how much the current paint should be blended with the background
		
		//first, we want to define a box to surround the round-shaped air brush
		int xStart = x-(AIRBRUSH_WIDTH/2);
		int yStart = y-(AIRBRUSH_WIDTH/2);
		
		
		//i and j are the actual locations of the pixel, rather than counters to adjust the location
		for(int i=xStart; i<xStart+AIRBRUSH_WIDTH; i++)//loop through the points in the box and check to see if the points are within the inscribed circular air-brush
		{
			for(int j=yStart; j<yStart+AIRBRUSH_WIDTH; j++)
			{
				int sqrtArgument = (x-i)*(x-i) + (y-j)*(y-j);
				double distance = Math.sqrt(sqrtArgument);//use the distance formula to find the distance between the center of the brush and the point-to-check
				//Log.d("DISTANCE", "distance: "+distance);
				if(distance<=(AIRBRUSH_WIDTH/2))//if the point is in the circle for the brush, then we want to change the pixel color and do our color blending, otherwise do nothing because it's not part of the air-brush
				{
					//then calculate the percent of the background that should show through
					//we want the sum of the colors to be equal to 1 so we don't lose any color
					float backgroundR = (float) (distance/(AIRBRUSH_WIDTH/2));//the contribution from the background is based on the distance from the center of the brush
					float paintR = 1-backgroundR;
					
					//the sum of backgroundR and paintR equals 1, (distance/radius) + (1-(distance/radius)) = 1, we do this because we don't want to lose color
					int newR = (int) (backgroundR*Color.red(getPixel(i,j)) + paintR*Color.red(_color));//the new red channel is equal to the distance-weighted ratios of background color and painting color
					//Log.d("COLOR", "newRFloat: "+newR);
					
					//repeat the above color blending algorithm for the green and blue color channel components
					
					float backgroundG = (float) (distance/(AIRBRUSH_WIDTH/2));
					float paintG = 1-backgroundG;
					int newG =  (int) (backgroundG*Color.green(getPixel(i,j)) + paintG*Color.green(_color));
					//Log.d("COLOR", "newGFloat: "+newG);
					
					float backgroundB = (float) (distance/(AIRBRUSH_WIDTH/2));
					float paintB = 1-backgroundB;
					int newB =  (int) (backgroundB*Color.blue(getPixel(i,j)) + paintB*Color.blue(_color));
					//Log.d("COLOR", "newBFloat: "+newB);
					
					setPixel(i, j, Color.rgb(newR, newG, newB)); //set the pixel to be the combined color, giving the illusion of transparency and displaying the air-brush effect
				}
			}
		}
		
	}


	/*********
	 * You shouldn't need to modify anything below this point!
	 ********/
	
	/**
	 * We need to override all the constructors, since we don't know which will be called
	 * All the constructors eventually call init()
	 */
	public MiniPaintView(Context context) {
		this(context, null);
	}

	public MiniPaintView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public MiniPaintView(Context context, AttributeSet attrs, int defaultStyle) {
		super(context, attrs, defaultStyle);
		init(context);
	}

	/**
	 * Our initialization method (called from constructors)
	 */
	public void init(Context context)
	{
		_context = context;
		_holder = this.getHolder(); //handles control of the surface
		_holder.addCallback(this);
		_thread = new DrawingThread(_holder, this);

		_scaleM = new Matrix();
		_scaleM.setScale(PIXEL_SIZE, PIXEL_SIZE);
		DELAY_MODE = true;
		
		_mode = POINT_MODE;
		_color = Color.WHITE;

		_startX = -1; //initialize as invalid
		_startY = -1;

		setFocusable(true); //just in case for touch events
	}
	
	
	/**
	 * Sets the drawing mode (UI method)
	 */
	public void setMode(int mode)
	{
		_mode = mode;
		_startX = -1;
		_startY = -1;
		//Toast toast = Toast.makeText(_context, "Mode set: "+_mode, Toast.LENGTH_SHORT);
		//toast.show();
	}

	/**
	 * Sets the painting color (UI method)
	 */
	public void setColor(int color)
	{
		_color = color;
		//Toast toast = Toast.makeText(_context, "Color set: "+_color, Toast.LENGTH_SHORT);
		//toast.show();
	}

	/**
	 * Clears the drawing (resets all pixels to Black)
	 */
	public void clearDrawing()
	{
		for(int i=0; i<_width; i++)
			for(int j=0; j<_height; j++)
				_bmp.setPixel(i, j, Color.BLACK);
	}

	/**
	 * Helper method to set a single pixel to a given color.
	 * Performed clipping, and includes debug settings to introduce a delay in pixel drawing
	 * @param x x-coord of pixel
	 * @param y y-coord of pixel
	 * @param color color to apply to pixel
	 */
	public void setPixel(int x, int y, int color)
	{
		/*Can comment out this block to make things go even faster*/
		if(DELAY_MODE) //if we're in delay mode, then pause while drawing
		{
			try{
				Thread.sleep(DELAY_TIME);
			} catch (InterruptedException e){}
		}
		
		if(x >= 0 && x < _width && y >= 0 && y < _height) //clipping for generated shapes (so we don't try and draw outside the bmp)
			_bmp.setPixel(x, y, color);
	}

	/**
	 * Helper method to set a single pixel to the current paint color.
	 * Performed clipping, and includes debug settings to introduce a delay in pixel drawing
	 * @param x x-coord of pixel
	 * @param y y-coord of pixel
	 */
	public void setPixel(int x, int y)
	{
		setPixel(x,y,_color);
	}
	
	/**
	 * Convenience method to get the color of a specific pixel
	 * @param x x-coord of pixel
	 * @param y y-coord of pixel
	 * @return The color of the pixel
	 */
	public int getPixel(int x, int y)
	{
		return _bmp.getPixel(x,y);
	}
	
	//called when the surface changes (like sizes changes due to rotate). Will need to respond accordingly.
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
	{
		//store new size for our BitMap
		_width = width/2;
		_height = height/2;

		//create a properly-sized bitmap to draw on
		_bmp = Bitmap.createBitmap(_width, _height, Bitmap.Config.ARGB_8888);
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) { //initialization stuff
		_thread.setRunning(true);
		_thread.start();
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) { //cleanup
		//Tell the thread to shut down, but wait for it to stop!
		_thread.setRunning(false);
		boolean retry = true;
		while(retry) {
			try {
				_thread.join();
				retry = false;
			} catch (InterruptedException e) {
				//will try again...
			}
		}
		Log.d(TAG, "Drawing thread shut down.");
	}

	/**
	 * An inner class representing a thread that does the drawing. Animation timing could go in here.
	 * http://obviam.net/index.php/the-android-game-loop/ has some nice details about using timers to specify animation
	 */
	public class DrawingThread extends Thread 
	{
		private boolean _isRunning; //whether we're running or not (so we can "stop" the thread)
		private SurfaceHolder _holder; //the holder we're going to post updates to
		private MiniPaintView _view; //the view that has drawing details

		/**
		 * Constructor for the Drawing Thread
		 * @param holder
		 * @param view
		 */
		public DrawingThread(SurfaceHolder holder, MiniPaintView view)
		{
			super();
			this._holder = holder;
			this._view = view;
			this._isRunning = false;
		}

		/**
		 * Executed when we call thread.start()
		 */
		@Override
		public void run()
		{
			Canvas canvas;
			while(_isRunning)
			{
				canvas = null;
				try {
					canvas = _holder.lockCanvas();
					synchronized (_holder) {
						canvas.drawBitmap(_bmp,_scaleM,null); //draw the _bitmap onto the canvas. Note that filling the bitmap occurs elsewhere
					}
				} finally { //no matter what (even if something goes wrong), make sure to push the drawing so isn't inconsistent
					if (canvas != null) {
						_holder.unlockCanvasAndPost(canvas);
					}
				}
			}
		}

		/**
		 * Public toggle for whether the thread is running.
		 * @param isRunning
		 */
		public void setRunning(boolean isRunning){
			this._isRunning = isRunning;
		}
	}
}
