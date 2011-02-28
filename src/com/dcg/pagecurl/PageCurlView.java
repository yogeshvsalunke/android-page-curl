package com.dcg.pagecurl;

import java.lang.ref.WeakReference;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;
import android.view.MotionEvent;
import android.view.View;

public class PageCurlView extends View {
	/** Px / Draw call */
	private static int CURL_SPEED = 30;

	/** Fixed update time used to create a smooth curl animation */
	private static long FIXED_UPDATE_TIME = 33;
	
	/** The initial offset for x and y axis movements */
	private static int CURL_START_OFFSET = 20;
	
	/** 
	 * Define the flip raius, this is the maximum radius a page 
	 * can be flipped.
	 */
	private float mFlipRadius;
	
	/** Point used to move */
	private Vector2D mMovement =  new Vector2D(0,0);
	
	/** The finger position */
	private Vector2D mFinger = new Vector2D(0,0);
	
	/** Movement point form the last frame */
	private Vector2D mOldMovement = new Vector2D(0,0);

	/** Handler used to auto flip time based */
	private FlipAnimationHandler mAnimationHandler;
	
	/** The context which owns us */
	private WeakReference<Context> mContext;
	
	/** Defines the flip direction that is currently considered */
	private boolean bFlipRight;
	
	/** If TRUE we are currently auto-flipping */
	private boolean bFlipping;
	
	/** TRUE if the user moves the pages */
	private boolean bUserMoves;

	/** Used to control touch input blocking */
	private boolean bBlockTouchInput = false;
	
	/** Enable input after the next draw event */
	private boolean bEnableInputAfterDraw = false;
	
	/** If TRUE we'll try to follow the finger in a possible manner */
	private boolean bFollowFinger = false;
	
	/** Our points used to define the current clipping paths in our draw call */
	private Vector2D mA, mB, mC, mD, mE, mF, mOldF, mOrigin;

	/** The current foreground */
	private Bitmap mForeground;
	
	/** The current background */
	private Bitmap mBackground;
	
	/** Page curl edge edge */
	private Paint mCurlEdgePaint;
	
	/** If false no draw call has been done */
	private boolean bViewDrawn;
	
	/** Simple text paints */
	private Paint mTextPaint = new Paint();
	private Paint mTextPaintShadow = new Paint();
	
	/** If true draw debug info of the current curl */
	public boolean bDrawDebug;
	
	private String DebugText;

	/**
	 * Create the view with just a context
	 * @param context
	 */
	public PageCurlView(Context context, boolean bFollowFinger) {
		super(context);
		InitView(context, bFollowFinger);
		
		// The focus flags are needed
		setFocusable(true);
		setFocusableInTouchMode(true);
	}

	/**
	 * Initialize the view with a given context
	 */
	private void InitView(Context context, boolean bFollowFinger) {
		mContext = new WeakReference<Context>(context);
		
		// Create some sample images
		mBackground = BitmapFactory.decodeResource(context.getResources(),
				R.drawable.page1);
		mForeground = BitmapFactory.decodeResource(context.getResources(),
				R.drawable.page2);
		
		// Set method
		// TODO: Add a nice method enum
		this.bFollowFinger = bFollowFinger;
		
		// Create our curl animation handler
		mAnimationHandler = new FlipAnimationHandler();
		
		// Create our edge paint
		mCurlEdgePaint = new Paint();
		mCurlEdgePaint.setColor(Color.WHITE);
		mCurlEdgePaint.setAntiAlias(true);
		mCurlEdgePaint.setStyle(Paint.Style.FILL);
		mCurlEdgePaint.setShadowLayer(10, -5, 5, 0x99000000);
		
		// Base text paints
		mTextPaint.setARGB(255, 0, 0, 0);
		mTextPaintShadow.setARGB(255, 0, 0, 0);
		
		// Initialize edges and positions
		ResetClipEdge();
	}
	
	/**
	 * Return the context which created use. Can return null if the
	 * context has been ereased
	 */
	private Context GetContext() {
		return mContext.get();
	}
	
	/**
	 * Reset points to it's initial clip edge state
	 */
	public void ResetClipEdge()
	{
		// Set our base movement
		mMovement.x = CURL_START_OFFSET;
		mMovement.y = CURL_START_OFFSET;		
		mOldMovement.x = 0;
		mOldMovement.y = 0;		
		
		// Now set the points		
		mA = new Vector2D(CURL_START_OFFSET, 0);
		mB = new Vector2D(this.getWidth(), this.getHeight());
		mC = new Vector2D(this.getWidth(), 0);
		mD = new Vector2D(0, 0);
		mE = new Vector2D(0, 0);
		mF = new Vector2D(0, 0);		
		mOldF = new Vector2D(0, 0);
		
		// The movement origin point
		mOrigin = new Vector2D(this.getWidth(), 0);
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (!bBlockTouchInput) {
			
			// Get our finger position
			mFinger.x = event.getX();
			mFinger.y = event.getY();
			int width = getWidth();
			//int height = getHeight();
			
			// Depending on the action do what we need to
			switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:				
				mOldMovement.x = mFinger.x;
				mOldMovement.y = mFinger.y;
				
				// If we moved over the half of the display flip to next
				if (mOldMovement.x > (width >> 1)) {
					mMovement.x = CURL_START_OFFSET;
					mMovement.y = CURL_START_OFFSET;
					
					// Set the right movement flag
					bFlipRight = true;
				} else {
					// Set the left movement flag
					bFlipRight = false;
					
					// Swap right views
					SwapViews();
					
					// Set new movement
					mMovement.x = bFollowFinger?width<<1:width;
					mMovement.y = CURL_START_OFFSET;
				}
				
				break;
			case MotionEvent.ACTION_UP:				
				bUserMoves=false;
				bFlipping=true;
				FlipAnimationStep();
				break;
			case MotionEvent.ACTION_MOVE:
				bUserMoves=true;
				
				// Get movement
				mMovement.x -= mFinger.x - mOldMovement.x;
				mMovement.y -= mFinger.y - mOldMovement.y;
				mMovement = CapMovement(mMovement, true);
				//mMovement = CapMovement(mFinger, true);
				//mMovement.x = width - mMovement.x;
				//mMovement.y = height - mMovement.y;
				
				// Make sure the y value get's locked at a nice level
				if ( mMovement.y  <= 1 )
					mMovement.y = 1;
				
				// Get movement direction
				if (mFinger.x < mOldMovement.x ) {
					bFlipRight = true;
				} else {
					bFlipRight = false;
				}
				
				// Save old movement values
				mOldMovement.x  = mFinger.x;
				mOldMovement.y  = mFinger.y;
				
				// Force a new draw call
				DoPageCurl();
				this.invalidate();
				break;
			}

		}
		
		// TODO: Only consume event if we need to.
		return true;
	}
	
	/**
	 * Make sure we never move too much, and make sure that if we 
	 * move too much to add a displacement so that the movement will 
	 * be still in our radius.
	 * @param radius - radius form the flip origin
	 * @param bMaintainMoveDir - Cap movement but do not change the
	 * current movement direction
	 * @return Corrected point
	 */
	private Vector2D CapMovement(Vector2D point, boolean bMaintainMoveDir)
	{
		// Make sure we never ever move too much
		if (point.distance(mOrigin) > mFlipRadius)
		{
			if ( bMaintainMoveDir )
			{
				// Maintain the direction
				point = mOrigin.sum(point.sub(mOrigin).normalize().mult(mFlipRadius));
			}
			else
			{
				// Change direction
				if ( point.x > (mOrigin.x+mFlipRadius))
					point.x = (mOrigin.x+mFlipRadius);
				else if ( point.x < (mOrigin.x-mFlipRadius) )
					point.x = (mOrigin.x-mFlipRadius);
				point.y = (float) (Math.sin(Math.acos(Math.abs(point.x-mOrigin.x)/mFlipRadius))*mFlipRadius);
			}
		}
		return point;
	}

	/**
	 * Execute a step of the flip animation
	 */
	public void FlipAnimationStep() {
		if ( !bFlipping )
			return;
		
		int width = getWidth();
			
		// No input when flipping
		bBlockTouchInput = true;
		
		// Handle speed
		float curlSpeed = CURL_SPEED;
		if ( !bFlipRight )
			curlSpeed *= -1;
		
		// Move us
		mMovement.x += curlSpeed;
		mMovement = CapMovement(mMovement, false);
		
		// Create values
		DoPageCurl();

		// Math.abs(mMovement.x-origin.x)/radius
		//if (mMovement.x >= (bFollowFinger?width<<1:width) || mMovement.x < CURL_START_OFFSET) {
		if (mA.x < 1 || mA.x > width - 1) {
			bFlipping = false;
			if (bFlipRight) {
				SwapViews();
			} 
			ResetClipEdge();
			
			// Create values
			DoPageCurl();

			// Enable touch input after the next draw event
			bEnableInputAfterDraw = true;
		}
		else
		{
			mAnimationHandler.sleep(FIXED_UPDATE_TIME);
		}
		
		// Force a new draw call
		this.invalidate();
	}
	
	/**
	 * Do the page curl depending on the methods we are using
	 */
	private void DoPageCurl()
	{
		if(bFlipping){
			if ( bFollowFinger )
				doDynamicCurl();
			else
				doSimpleCurl();
			
		} else {
			if ( bFollowFinger )
				doDynamicCurl();
			else
				doSimpleCurl();
		}
	}
	
	/**
	 * Called on the first draw event of the view
	 * @param canvas
	 */
	protected void onFirstDrawEvent(Canvas canvas) {
		
		mFlipRadius = getWidth();
		
		ResetClipEdge();
		DoPageCurl();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		
		// We need to initialize all size data when we first draw the view
		if ( !bViewDrawn ) {
			bViewDrawn = true;
			onFirstDrawEvent(canvas);
		}
		
		canvas.drawColor(Color.WHITE);
		
		// Curl pages
		//DoPageCurl();
		
		// TODO: This just scales the views to the current
		// width and height. We should add some logic for:
		//  1) Maintain aspect ratio
		//  2) Uniform scale
		//  3) ...
		Rect rect = new Rect();
		rect.left = 0;
		rect.top = 0;
		rect.bottom = getHeight();
		rect.right = getWidth();
		
		// First Page render
		Paint paint = new Paint();
		
		// Draw our elements
		drawForeground(canvas, rect, paint);
		drawBackground(canvas, rect, paint);
		drawCurlEdge(canvas);
		
		// Draw any debug info once we are done
		if ( bDrawDebug )
			drawDebug(canvas);		
		
		// Check if we can re-enable input
		if ( bEnableInputAfterDraw )
		{
			bBlockTouchInput = false;
			bEnableInputAfterDraw = false;
		}

	}
	
	/**
	 * Draw debug info
	 * @param canvas
	 */
	private void drawDebug(Canvas canvas)
	{
		float posX = 10;
		float posY = 20;
		
		Paint paint = new Paint();
		paint.setStrokeWidth(5);
		paint.setStyle(Style.STROKE);
		
		paint.setColor(Color.BLACK);		
		canvas.drawCircle(mOrigin.x, mOrigin.y, getWidth(), paint);
		
		paint.setStrokeWidth(3);
		paint.setColor(Color.RED);		
		canvas.drawCircle(mOrigin.x, mOrigin.y, getWidth(), paint);
		
		paint.setStrokeWidth(5);
		paint.setColor(Color.BLACK);
		canvas.drawLine(mOrigin.x, mOrigin.y, mMovement.x, mMovement.y, paint);
		
		paint.setStrokeWidth(3);
		paint.setColor(Color.RED);
		canvas.drawLine(mOrigin.x, mOrigin.y, mMovement.x, mMovement.y, paint);
		
		posY = debugDrawPoint(canvas,"A",mA,Color.RED,posX,posY);
		posY = debugDrawPoint(canvas,"B",mB,Color.GREEN,posX,posY);
		posY = debugDrawPoint(canvas,"C",mC,Color.BLUE,posX,posY);
		posY = debugDrawPoint(canvas,"D",mD,Color.CYAN,posX,posY);
		posY = debugDrawPoint(canvas,"E",mE,Color.YELLOW,posX,posY);
		posY = debugDrawPoint(canvas,"F",mF,Color.LTGRAY,posX,posY);
		posY = debugDrawPoint(canvas,"Mov",mMovement,Color.DKGRAY,posX,posY);
		posY = debugDrawPoint(canvas,"Origin",mOrigin,Color.MAGENTA,posX,posY);
		posY = debugDrawPoint(canvas,"Finger",mFinger,Color.BLACK,posX,posY);
		
		if ( DebugText != null )
		{
			mTextPaint.setColor(Color.RED);
			drawTextShadowed(canvas, DebugText, posX, posY, mTextPaint,mTextPaintShadow);
			posY += 15;
		}
	}
	
	private float debugDrawPoint(Canvas canvas, String name, Vector2D point, int color, float posX, float posY) {	
		return debugDrawPoint(canvas,name+" "+point.toString(),point.x, point.y, color, posX, posY);
	}
	
	private float debugDrawPoint(Canvas canvas, String name, float X, float Y, int color, float posX, float posY) {
		mTextPaint.setColor(color);
		drawTextShadowed(canvas,name,posX , posY, mTextPaint,mTextPaintShadow);
		Paint paint = new Paint();
		paint.setStrokeWidth(5);
		paint.setColor(color);	
		canvas.drawPoint(X, Y, paint);
		return posY+15;
	}
	
	/**
	 * Draw the foreground
	 * @param canvas
	 * @param rect
	 * @param paint
	 */
	private void drawForeground( Canvas canvas, Rect rect, Paint paint ) {
		canvas.drawBitmap(mForeground, null, rect, paint);
	}
	
	/**
	 * Create a Path used as a mask to draw the background page
	 * @return
	 */
	private Path createBackgroundPath() {
		Path path = new Path();
		path.moveTo(mA.x, mA.y);
		path.lineTo(mB.x, mB.y);
		path.lineTo(mC.x, mC.y);
		path.lineTo(mD.x, mD.y);
		path.lineTo(mA.x, mA.y);
		return path;
	}
	
	/**
	 * Draw the background image.
	 * @param canvas
	 * @param rect
	 * @param paint
	 */
	private void drawBackground( Canvas canvas, Rect rect, Paint paint ) {
		Path mask = createBackgroundPath();
		canvas.clipPath(mask);
		canvas.drawBitmap(mBackground, null, rect, paint);
		canvas.restore();
	}
	
	/**
	 * Creates a path used to draw the curl edge in.
	 * @return
	 */
	private Path createCurlEdgePath() {
		Path path = new Path();
		path.moveTo(mA.x, mA.y);
		path.lineTo(mD.x, mD.y);
		path.lineTo(mE.x, mE.y);
		path.lineTo(mF.x, mF.y);
		path.lineTo(mA.x, mA.y);
		return path;
	}
	
	/**
	 * Draw the curl page edge
	 * @param canvas
	 */
	private void drawCurlEdge( Canvas canvas )
	{
		Path path = createCurlEdgePath();
		canvas.drawPath(path, mCurlEdgePaint);
	}

	/**
	 * Do a simple page curl effect
	 */
	private void doSimpleCurl() {
		int width = getWidth();
		int height = getHeight();
		
		// Calculate point A
		mA.x = width - mMovement.x;
		mA.y = height;

		// Calculate point D
		mD.x = 0;
		mD.y = 0;
		if (mA.x > width / 2) {
			mD.x = width;
			mD.y = height - (width - mA.x) * height / mA.x;
		} else {
			mD.x = 2 * mA.x;
			mD.y = 0;
		}
		
		// Now calculate E and F taking into account that the line
		// AD is perpendicular to FB and EC. B and C are fixed points.
		double angle = Math.atan((height - mD.y) / (mD.x + mMovement.x - width));
		double _cos = Math.cos(2 * angle);
		double _sin = Math.sin(2 * angle);

		// And get F
		mF.x = (float) (width - mMovement.x + _cos * mMovement.x);
		mF.y = (float) (height - _sin * mMovement.x);
		
		// If the x position of A is above half of the page we are still not
		// folding the upper-right edge and so E and D are equal.
		if (mA.x > width / 2) {
			mE.x = mD.x;
			mE.y = mD.y;
		}
		else
		{
			// So get E
			mE.x = (float) (mD.x + _cos * (width - mD.x));
			mE.y = (float) -(_sin * (width - mD.x));
		}
	}

	/**
	 * Calculate the dynamic effect, that one that follows the users finger
	 */
	private void doDynamicCurl() {
		int width = getWidth();
		int height = getHeight();

		// F will follow the finger, we add a small displacement
		// So that we can see the edge
		mF.x = width - mMovement.x+0.1f;
		mF.y = height - mMovement.y+0.1f;
		
		// Set min points
		if(mA.x==0){
			mF.x= Math.min(mF.x, mOldF.x);
			mF.y= Math.max(mF.y, mOldF.y);
		}
		
		// Get diffs
		float deltaX = width-mF.x;
		float deltaY = height-mF.y;

		float BH = (float) (Math.sqrt(deltaX * deltaX + deltaY * deltaY) / 2);
		double tangAlpha = deltaY / deltaX;
		double alpha = Math.atan(deltaY / deltaX);
		double _cos = Math.cos(alpha);
		double _sin = Math.sin(alpha);
		
		mA.x = (float) (width - (BH / _cos));
		mA.y = height;
		
		mD.y = (float) (height - (BH / _sin));
		mD.x = width;

		mA.x = Math.max(0,mA.x);
		if(mA.x==0){
			mOldF.x = mF.x;
			mOldF.y = mF.y;
		}
		
		// Get W
		mE.x = mD.x;
		mE.y = mD.y;
		
		// Correct
		if (mD.y < 0) {
			mD.x = width + (float) (tangAlpha * mD.y);
			mE.y = 0;
			mE.x = width + (float) (Math.tan(2 * alpha) * mD.y);
		}
	}

	/**
	 * Swap between the fore and back-ground
	 */
	private void SwapViews() {
		Bitmap temp = mForeground;
		mForeground = mBackground;
		mBackground = temp;
	}
	
	/**
	 * Draw a text with a nice shadow
	 */
	public static void drawTextShadowed(Canvas canvas, String text, float x, float y, Paint textPain, Paint shadowPaint) {
    	canvas.drawText(text, x-1, y, shadowPaint);
    	canvas.drawText(text, x, y+1, shadowPaint);
    	canvas.drawText(text, x+1, y, shadowPaint);
    	canvas.drawText(text, x, y-1, shadowPaint);    	
    	canvas.drawText(text, x, y, textPain);
    }
	
	/**
	 * Inner class used to represent a 2D point.
	 */
	private class Vector2D
	{
		public float x,y;
		public Vector2D(float x, float y)
		{
			this.x = x;
			this.y = y;
		}
		
		@Override
		public String toString() {
			// TODO Auto-generated method stub
			return "("+this.x+","+this.y+")";
		}
		
		 public float length() {
             return (float) Math.sqrt(x * x + y * y);
	     }
	
	     public float lengthSquared() {
	             return (x * x) + (y * y);
	     }
		
		public boolean equals(Object o) {
			if (o instanceof Vector2D) {
				Vector2D p = (Vector2D) o;
				return p.x == x && p.y == y;
	        }
	        return false;
		}
		
		public Vector2D reverse() {
			return new Vector2D(-x,-y);
		}
		
		public Vector2D sum(Vector2D b) {
            return new Vector2D(x+b.x,y+b.y);
		}
		
		public Vector2D sub(Vector2D b) {
            return new Vector2D(x-b.x,y-b.y);
		}		

		public float dot(Vector2D vec) {
            return (x * vec.x) + (y * vec.y);
		}

	    public float cross(Vector2D a, Vector2D b) {
	            return a.cross(b);
	    }
	
	    public float cross(Vector2D vec) {
	            return x * vec.y - y * vec.x;
	    }
	    
	    public float distanceSquared(Vector2D other) {
	    	float dx = other.x - x;
	    	float dy = other.y - y;

            return (dx * dx) + (dy * dy);
	    }
	
	    public float distance(Vector2D other) {
	            return (float) Math.sqrt(distanceSquared(other));
	    }
	    
	    public float dotProduct(Vector2D other) {
            return other.x * x + other.y * y;
	    }
		
		public Vector2D normalize() {
			float magnitude = (float) Math.sqrt(dotProduct(this));
            return new Vector2D(x / magnitude, y / magnitude);
		}
		
		public Vector2D mult(float scalar) {
	            return new Vector2D(x*scalar,y*scalar);
	    }
	}

	/**
	 * Inner class used to make a fixed timed animation of the curl effect.
	 */
	class FlipAnimationHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			PageCurlView.this.FlipAnimationStep();
		}

		public void sleep(long millis) {
			this.removeMessages(0);
			sendMessageDelayed(obtainMessage(0), millis);
		}
	}

}
