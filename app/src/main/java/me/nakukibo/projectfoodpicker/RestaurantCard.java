package me.nakukibo.projectfoodpicker;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Locale;

public class RestaurantCard extends ScrollView {

    // TODO: add distance and open now

    private static final String TAG = RestaurantCard.class.getSimpleName();

    private TextView txtvwName;
    private TextView txtvwOpenNow;
    private TextView txtvwDistance;
    private ImageView restPhoto;
    private RestaurantCardContents restaurantCardContents;

    private OnSwipe onSwipeEvent;

    private View viewLastPhoto;
    private View viewNextPhoto;
    private View viewOpenContents;

    // values for swiping
    private float restCardStartX;
    private float restCardStartY;
    private float restCardDx = 0;
    private float restCardDy = 0;
    boolean isBeingSwiped = false;

//    private static final String TAG = RestaurantCard.class.getSimpleName();

    public RestaurantCard(@NonNull Context context) {
        this(context, null);
    }

    public RestaurantCard(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RestaurantCard(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        onSwipeEvent = null;
        initCard(context, attrs);
        initSwipeVariables();
        initEvents();
    }

    /**
     * set restaurant card to values passed as HashMap<String, String> with keys used by DataParser class
     */
    void setValues(HashMap<String, String> values){
        String distMetersStr = values.get(DataParser.DATA_KEY_DISTANCE);
        Double distMiles = distMetersStr == null ? null : PreferencesActivity.metersToMiles(Float.parseFloat(distMetersStr));
        Log.d(TAG, "setValues: restaurant distance=" + distMetersStr + ", " + distMiles);
        String openNow = values.get(DataParser.DATA_KEY_CURRENTLY_OPEN);

        setValues(
                values.get(DataParser.DATA_KEY_NAME),
                openNow != null && openNow.equals("true"),
                distMiles,
                R.drawable.ic_launcher_background,
                String.format(Locale.US, "%s stars (%s)",
                        values.get(DataParser.DATA_KEY_RATING), values.get(DataParser.DATA_KEY_TOT_RATING)),
                String.format(Locale.US, "Pricing Level: %s",
                        values.get(DataParser.DATA_KEY_PRICE_LEVEL)),
                values.get(DataParser.DATA_KEY_ADDRESS),
                values.get(DataParser.DATA_KEY_PHONE_NUMBER),
                values.get(DataParser.DATA_KEY_WEBSITE),
                values.get(DataParser.DATA_KEY_HOURS)
        );
    }

    /**
     * set restaurant card to default values
     */
    void setDefaultValues(){
        setValues(
                getResources().getString(R.string.restcard_default_name),
                true,
                12.20,
                R.drawable.ic_launcher_background,
                getResources().getString(R.string.restcard_default_rating),
                getResources().getString(R.string.restcard_default_pricing),
                getResources().getString(R.string.restcard_default_address),
                getResources().getString(R.string.restcard_default_phone_number),
                getResources().getString(R.string.restcard_default_website),
                getResources().getString(R.string.restcard_default_hours));
    }

    /**
     * set restaurant card to values passed
     */
    private void setValues(String name, Boolean openNow, Double distanceMiles, int photoSource, String rating, String pricing, String address,
                           String phoneNumber, String website, String hours){
        txtvwName.setText(name);

        String openNowText;
        if(openNow){
            openNowText = "Open Now!";
        }else {
            openNowText = "Closed";
        }
        txtvwOpenNow.setText(openNowText);

        txtvwDistance.setText(distanceMiles == null? "Unknown Distance" : String.format(Locale.US, "%.2f miles", distanceMiles));
        restPhoto.setImageResource(photoSource);
        restaurantCardContents.setValues(rating, pricing, address, phoneNumber, website, hours);
    }

    private void initSwipeVariables() {
        restCardStartX = this.getX();
        restCardStartY = this.getY();
    }

    /**
     * initialize the views in the card to specific values
     */
    private void initCard(@NonNull Context context, @Nullable AttributeSet attrs) {
        inflate(context, R.layout.restaurant_card, this);

        txtvwName = findViewById(R.id.txtvw_title_name);
        txtvwOpenNow = findViewById(R.id.txtvw_title_open_now);
        txtvwDistance = findViewById(R.id.txtvw_title_distance);
        restPhoto = findViewById(R.id.imgvw_title_restaurant);
        restaurantCardContents = findViewById(R.id.restcard_contents);

        viewLastPhoto = findViewById(R.id.view_last_image);
        viewNextPhoto = findViewById(R.id.view_next_image);
        viewOpenContents = findViewById(R.id.view_open_contents);

        restaurantCardContents.setVisibility(GONE);

        // set view values to attribute values
        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.RestaurantCard);
        // TODO: make attributes for distance and open_now
        setValues(
                attributes.getString(R.styleable.RestaurantCard_name),
                true,
                12.20,
                R.styleable.RestaurantCard_android_src,
                String.format(Locale.US, "%1.1f stars", attributes.getFloat(R.styleable.RestaurantCard_rating, 1f)),
                String.format(Locale.US, "Price Level: %d", attributes.getInteger(R.styleable.RestaurantCard_price_level, 1)),
                attributes.getString(R.styleable.RestaurantCard_address),
                attributes.getString(R.styleable.RestaurantCard_phone_number),
                attributes.getString(R.styleable.RestaurantCard_website),
                attributes.getString(R.styleable.RestaurantCard_hours)
        );

        attributes.recycle();
    }

    private void initEvents() {
        viewLastPhoto.setOnTouchListener((view, motionEvent) -> {
            Log.d(TAG, "initEvents: viewLastPhoto touch event");

            if(cannotPerformEvents()) return true;
            boolean isSwiped = checkForSwipe(motionEvent);

            if(motionEvent.getAction() == MotionEvent.ACTION_UP && !isSwiped){
                Log.d(TAG, "initEvents: viewing last image");
            }

            return true;
        });

        viewNextPhoto.setOnTouchListener((view, motionEvent) -> {
            Log.d(TAG, "initEvents: viewNextPhoto touch event");

            if(cannotPerformEvents()) return true;
            boolean isSwiped = checkForSwipe(motionEvent);

            if(motionEvent.getAction() == MotionEvent.ACTION_UP && !isSwiped){
                Log.d(TAG, "initEvents: viewing next image");
            }

            return true;
        });

        viewOpenContents.setOnTouchListener((view, motionEvent) -> {
            Log.d(TAG, "initEvents: viewOpenContents touch event");

            if(cannotPerformEvents()) return true;
            boolean isSwiped = checkForSwipe(motionEvent);

            if(motionEvent.getAction() == MotionEvent.ACTION_UP && !isSwiped){
                Log.d(TAG, "initEvents: opening contents");
                if(!isContentsVisible()) {
                    restaurantCardContents.setVisibility(VISIBLE);
                } else {
                        restaurantCardContents.setVisibility(GONE);
                }
            }

            return true;
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if(isContentsVisible()){
            return super.onTouchEvent(ev);
        }  else {
            Log.d(TAG, "initEvents: RestaurantCard touch event");

            if(cannotPerformEvents()) return true;
            checkForSwipe(ev);
            return true;
        }
    }

    private boolean cannotPerformEvents(){
        return this.getVisibility() != View.VISIBLE;
    }

    private boolean checkForSwipe(MotionEvent motionEvent){
        if(isContentsVisible()) return false;

        final float minDistance = 30f;

        float width = this.getWidth();
        float newX = motionEvent.getRawX() + restCardDx;
        float newY = motionEvent.getRawY() + restCardDy;

        switch(motionEvent.getAction()){
            case MotionEvent.ACTION_DOWN:
                restCardDx = this.getX() - motionEvent.getRawX();
                restCardDy = this.getY() - motionEvent.getRawY();
                break;

            case MotionEvent.ACTION_MOVE:
                float dx = newX - restCardStartX;
                float dy = newY - restCardStartY;

                if(dx*dx + dy*dy >= minDistance*minDistance) {
                    isBeingSwiped = true;
                    Log.d(TAG, "checkForSwipe: restaurantCard set to being swiped:" + dx + "," + dy);
                }else {
                    Log.d(TAG, "checkForSwipe: not being swiped");
                }

                if(isBeingSwiped){
                    this.setX(newX);
                    this.setY(newY);
                }
                break;

            case MotionEvent.ACTION_UP:
                // if pass threshold, then new card, else place card back in center
                if(isBeingSwiped){

                    if (newX <= restCardStartX - width / 2) {
                        Log.d(TAG, "initViews: card is swiped left");
                        this.startAnimation(outToLeftAnimation());
                        if(onSwipeEvent != null) onSwipeEvent.onSwipe();
                    }

                    this.setX(restCardStartX);
                    this.setY(restCardStartY);

                    isBeingSwiped = false;
                    return true;
                }
        }
        return false;
    }

    /**
     * Animation for a card to move from in card to out of screen
     * */
    private Animation outToLeftAnimation() {
        Animation outToLeft = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, -1.0f,
                Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, 0.0f);
        outToLeft.setDuration(200);
        outToLeft.setInterpolator(new AccelerateInterpolator());
        return outToLeft;
    }

    public boolean isContentsVisible(){
        return restaurantCardContents.getVisibility() == VISIBLE;
    }

    public void setOnSwipeEvent(OnSwipe onSwipeEvent){
        this.onSwipeEvent = onSwipeEvent;
    }
}
