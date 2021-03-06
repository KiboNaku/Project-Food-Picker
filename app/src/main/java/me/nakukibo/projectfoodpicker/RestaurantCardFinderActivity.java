package me.nakukibo.projectfoodpicker;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.JsonArray;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class RestaurantCardFinderActivity extends CustomAppCompatActivity implements GetNearbyData.ReceiveNearbyData {

    private static final String TAG = RestaurantCardFinderActivity.class.getSimpleName();

    private View loadingView;
    private View wrapperOpen;
    private View wrapperClose;

    private ConstraintLayout noRestaurantsError;
    private ConstraintLayout buttonSet;

    private FloatingActionButton btnSwipe;
    private FloatingActionButton btnBlock;
    private FloatingActionButton btnOpenContents;
    private FloatingActionButton btnCloseContents;

    private RestaurantCard restCard1;
    private RestaurantCard restCard2;
    private RestaurantCard activeCard;

    private List<Restaurant> nearbyRestaurants;
    private LinkedList<Restaurant> processedRestaurants;
    private List<Restaurant> previouslyAccessed;

    private boolean firstCard;
    private boolean needToSetCard;

    private GetNearbyData getNearbyData;
    private FetchDetails fetchDetails;

    private boolean fetchedNearbyData;

    // data passed from PreferencesActivity.java
    private String foodType;
    private int distance;
    private int pricing;
    private int rating;
    private Boolean openNow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_restaurant_card_finder);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        initViewVariables();
        initViewEvents();
        initViewValues();
        resetGlobalVariables();

        retrievePassedValues();
        fetchLocation();
    }

    @Override
    public void onBackPressed(){
        Log.d(TAG, "onBackPressed: back pressed");
        if(!fetchedNearbyData && getNearbyData != null){
            Log.d(TAG, "onBackPressed: cancelling nearby fetch");
            getNearbyData.cancel(true);
        }

        if(fetchDetails != null){
            Log.d(TAG, "onBackPressed: cancelling details fetch");
            fetchDetails.cancel(true);
        }

        finish();
    }

    private void initViewEvents() {

        RestaurantCard.OnOpenContents onOpenContents = () -> {
            Log.d(TAG, "initViewEvents: opening contents");

            btnOpenContents.setClickable(false);
            wrapperOpen.setVisibility(View.INVISIBLE);
            btnOpenContents.hide();

            btnCloseContents.setClickable(true);
            wrapperClose.setVisibility(View.VISIBLE);
            btnCloseContents.show();

            btnSwipe.setClickable(false);
            btnSwipe.hide();
        };

        RestaurantCard.OnCloseContents onCloseContents = () -> {
            Log.d(TAG, "initViewEvents: closing contents");

            btnCloseContents.setClickable(false);
            wrapperClose.setVisibility(View.INVISIBLE);
            btnCloseContents.hide();

            btnOpenContents.setClickable(true);
            wrapperOpen.setVisibility(View.VISIBLE);
            btnOpenContents.show();

            btnSwipe.setClickable(true);
            btnSwipe.show();
        };

        restCard1.setOnSwipeStartEvent(this::deactivateFloatingButtons);
        restCard2.setOnSwipeStartEvent(this::deactivateFloatingButtons);
        restCard1.setOnSwipeEndEvent(() -> defaultSwipeEndEvent(restCard1, restCard2));
        restCard2.setOnSwipeEndEvent(() -> defaultSwipeEndEvent(restCard2, restCard1));

        restCard1.setOnOpenContents(onOpenContents);
        restCard2.setOnOpenContents(onOpenContents);
        restCard1.setOnCloseContents(onCloseContents);
        restCard2.setOnCloseContents(onCloseContents);
    }

    private void resetGlobalVariables() {
        Places.initialize(getApplicationContext(), getResources().getString(R.string.google_maps_key));
        nearbyRestaurants = new ArrayList<>();
        processedRestaurants = new LinkedList<>();
        previouslyAccessed = getPreviouslyAccessed();

        firstCard = true;
        needToSetCard = true;
        fetchedNearbyData = false;
        getNearbyData = null;
        fetchDetails = null;
    }

    private void initViewVariables() {
        loadingView = findViewById(R.id.restcard_loading);
        wrapperOpen = findViewById(R.id.btn_open_wrapper);
        wrapperClose = findViewById(R.id.btn_close_wrapper);
        noRestaurantsError = findViewById(R.id.no_restaurants_layout);
        buttonSet = findViewById(R.id.restcard_finder_btn_set);

        btnSwipe = findViewById(R.id.btn_roll_again);
        btnBlock = findViewById(R.id.btn_block_location);
        btnOpenContents = findViewById(R.id.btn_open_contents);
        btnCloseContents = findViewById(R.id.btn_close_contents);

        restCard1 = findViewById(R.id.restcard);
        restCard2 = findViewById(R.id.restcard2);
        restCard1.resetCard();
        restCard2.resetCard();
        activeCard = null;
    }

    private void initViewValues() {
        loadingView.setVisibility(View.VISIBLE);
        noRestaurantsError.setVisibility(View.GONE);
        buttonSet.setVisibility(View.INVISIBLE);

        deactivateFloatingButtons();

        restCard1.setDefaultValues();
        restCard2.setDefaultValues();
        restCard1.setVisibility(View.INVISIBLE);
        restCard2.setVisibility(View.INVISIBLE);
    }

    /**
     * restore values passed in from PreferencesActivity.java
     */
    private void retrievePassedValues() {
        foodType = getIntent().getStringExtra(PreferencesActivity.PREF_INTENT_FOOD_TYPE);
        if(foodType == null) foodType = PreferencesActivity.getDefaultFoodType();

        rating = getIntent().getIntExtra(
                PreferencesActivity.PREF_INTENT_RATING,
                PreferencesActivity.getDefaultRating()
        );

        distance = getIntent().getIntExtra(
                PreferencesActivity.PREF_INTENT_DISTANCE,
                PreferencesActivity.getDefaultDistanceMeters(getApplicationSharedPreferences()
                        .getInt(getString(R.string.sp_distance_margin), SettingsActivity.MARGIN_MULTIPLIER))
        );

        pricing = getIntent().getIntExtra(
                PreferencesActivity.PREF_INTENT_PRICING,
                PreferencesActivity.getDefaultPriceRange()
        );

        openNow = getIntent().getBooleanExtra(
                PreferencesActivity.PREF_INTENT_OPEN_NOW,
                PreferencesActivity.getDefaultIsOpen()
        );
    }

    /**
     * fetch user current location and find the nearby restaurants based on preferences
     */
    private void fetchLocation() {
        // if no rolls remaining, then error
        if(outOfRolls()){
            showRestaurantError(R.string.restcard_finder_no_rolls);
            return;
        }

        FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    // Got last known location. In some rare situations this can be null.
                    if (location != null) {
                        // get locational information
                        double latitude = location.getLatitude();
                        double longitude = location.getLongitude();
                        Object[] dataTransfer = new Object[7];
                        Log.d(TAG, "fetchLocation: calling getnearbydata");

                        // find restaurants
                        getNearbyData = new GetNearbyData(getResources().getString(R.string.google_maps_key),
                                this, nearbyRestaurants);
                        String url = getUrl(latitude, longitude);
                        dataTransfer[0] = url;
                        dataTransfer[1] = location;
                        dataTransfer[2] = distance;
                        dataTransfer[3] = pricing;
                        dataTransfer[4] = rating;
                        dataTransfer[5] = getApplicationSharedPreferences()
                                .getBoolean(getResources()
                                        .getString(R.string.sp_allow_prominent), false);
                        dataTransfer[6] = openNow;

                        Log.d(TAG, "fetchLocation: openNow=" + openNow);
                        getNearbyData.execute(dataTransfer);
                    }
                });
    }

    /**
     * called when the GetNearbyData AsyncTask has finished retrieving the information for all of the restaurants
     * nearby
     */
    @Override
    public void onFinishNearbyFetch(){
        fetchedNearbyData = true;

        Log.d(TAG, "onFinishNearbyFetch: combined list has a size of " + nearbyRestaurants.size());
        logAllPlacesList(nearbyRestaurants);

        final PlacesClient placesClient = Places.createClient(this);
        LinkedList<Restaurant> unvisitedRestaurants = new LinkedList<>(removeVisited(nearbyRestaurants));
        Collections.shuffle(unvisitedRestaurants);

        Restaurant.OnFinishRetrievingImages onFinishRetrievingImages = new Restaurant.OnFinishRetrievingImages() {
            @Override
            public void onFinishRetrieve(Restaurant restaurant) {
                onFinishDetailsFetch(restaurant);
            }
        };
        fetchDetails = new FetchDetails(unvisitedRestaurants, onFinishRetrievingImages);
        fetchDetails.execute(placesClient);
    }

    private void onFinishDetailsFetch(Restaurant selectedRestaurant){
        if(buttonSet.getVisibility() == View.VISIBLE){
            Log.d(TAG, "onFinishDetailsFetch: button set visible");
        }else {
            Log.d(TAG, "onFinishDetailsFetch: button set not visible");
        }


        Log.d(TAG, "onFinishDetailsFetch: finished fetching details for " + selectedRestaurant.getName());

        if (firstCard) {
            firstCard = false;
            hideLoadingScreen();
            activateFloatingButtons();

            btnOpenContents.show();
            btnCloseContents.hide();
            wrapperOpen.setVisibility(View.VISIBLE);
            activeCard = restCard1;
            activeCard.setVisibility(View.VISIBLE);
            buttonSet.setVisibility(View.VISIBLE);
        }

        if(needToSetCard){
            hideLoadingScreen();
            activateFloatingButtons();
            needToSetCard = false; // hint: you can make it swipe automatically if u set this to true
            makeRoll(selectedRestaurant);
        }else {
            processedRestaurants.add(selectedRestaurant);
            Log.d(TAG, "sendDetailData: adding to processedRestaurants");
            logAllPlacesList(processedRestaurants);
        }
    }

    private void fetchNextRestaurant(int errorTxt) {
        if(outOfRolls()){
            showRestaurantError(R.string.restcard_finder_no_rolls);
            return;
        }else if(!haveUnseenRestaurants()) {
            showRestaurantError(errorTxt);
            return;
        }

        if(processedRestaurants.size() > 0){
            hideLoadingScreen();
            activateFloatingButtons();
            Restaurant nextRestaurant = processedRestaurants.pop();
            makeRoll(nextRestaurant);
        } else{
            needToSetCard = true;
            showLoadingScreen();
            deactivateFloatingButtons();
        }
    }

    private void showRestaurantError(int errorTxt){
        deactivateFloatingButtons();
        hideLoadingScreen();
        hideCards();
        TextView txtvwNoRestaurants = findViewById(R.id.txtvw_no_restaurants);
        txtvwNoRestaurants.setText(getResources().getString(errorTxt));
        noRestaurantsError.setVisibility(View.VISIBLE);
    }

    private boolean haveUnseenRestaurants() {
        Set<Restaurant> potentials = removeVisited(nearbyRestaurants);
        Log.d(TAG, "haveUnseenRestaurants: unseen restaurants:" + potentials.size());
        return potentials.size() > 0;
    }

    private boolean outOfRolls(){
        int remainingRolls = getApplicationSharedPreferences().getInt(getString(R.string.sp_remained_rerolls), 10);
        Log.d(TAG, "haveUnseenRestaurants: remainingRolls = " + remainingRolls);

        return remainingRolls <= 0;
    }

    private void makeRoll(Restaurant selectedRestaurant){
        setViewValues(selectedRestaurant);

        int remainingRolls = getApplicationSharedPreferences().getInt(getString(R.string.sp_remained_rerolls), 10);
        Log.d(TAG, "makeRoll: remainingRolls=" + remainingRolls);

        SharedPreferences.Editor editor = getApplicationSharedPreferences().edit();
        editor.putInt(getString(R.string.sp_remained_rerolls), remainingRolls);
        editor.apply();
    }


    private void defaultSwipeEndEvent(RestaurantCard thisCard, RestaurantCard otherCard) {
        btnOpenContents.setClickable(false);
        btnCloseContents.setClickable(false);
        thisCard.setVisibility(View.GONE);
        thisCard.setDefaultValues();
        thisCard.resetCard();
        activeCard = otherCard;
        fetchNextRestaurant(R.string.restcard_finder_no_more_restaurants);
    }

    /**
     * set values of views to values in HashMap<String, String>
     */
    private void setViewValues(Restaurant selectedRestaurant) {
        previouslyAccessed.add(selectedRestaurant);

        Animation inAnimation = inFromRightAnimation();
        inAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                activateFloatingButtons();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });

        activeCard.setVisibility(View.VISIBLE);
        activeCard.setValues(selectedRestaurant);
        activeCard.setAnimation(inAnimation);
    }

    // TODO: fix this
    private Set<Restaurant> removeVisited(List<Restaurant> list){
        Set<Restaurant> potentials = new HashSet<>(list);
        potentials.removeAll(previouslyAccessed);
        return potentials;
    }

    // TODO: rewrite using Restaurant classes
    private void savePreviouslyAccessedData() {

        Log.d(TAG, "savePreviouslyAccessedData: saving previously accessed restaurants");

        final String jsonArrayStr = getApplicationSharedPreferences().getString(getString(R.string.sp_previously_accessed_json), null);
        JSONArray jsonArray = new JSONArray();

        try{
            JSONObject jsonArrayObj = jsonArrayStr == null ? new JSONObject() : new JSONObject(jsonArrayStr);
            jsonArray = jsonArrayObj.getJSONArray(getString(R.string.sp_previously_accessed_json));
        } catch (JSONException e){
            e.printStackTrace();
        }

        List<Restaurant> storedRestaurants = getPreviouslyAccessed();

        for(Restaurant restaurant: previouslyAccessed){
            boolean add = true;

            for(int i=0; i<storedRestaurants.size(); i++){
                if(storedRestaurants.get(i).getId().equals(restaurant.getId())) add = false;
            }

            if(add) jsonArray.put(restaurant.getJsonFromRestaurant());
        }

        try {
            JSONObject jsonArrayObj = new JSONObject();
            jsonArrayObj.put(getString(R.string.sp_previously_accessed_json), jsonArray);

            Log.d(TAG, "savePreviouslyAccessedData: saving jsonArrayObj=" + jsonArrayObj.toString());

            SharedPreferences.Editor editor = getApplicationSharedPreferences().edit();
            editor.putString(getString(R.string.sp_previously_accessed_json), jsonArrayObj.toString());
            editor.apply();

        } catch (JSONException e){
            e.printStackTrace();
            Log.e(TAG, "savePreviouslyAccessedData: failed to save jsonArray");
        }
    }

    private List<Restaurant> getPreviouslyAccessed() {

        List<Restaurant> restaurantList = new ArrayList<>();

        final String jsonArrayStr = getApplicationSharedPreferences().getString(getString(R.string.sp_previously_accessed_json), null);
        JSONArray jsonArray = null;

        try{
            JSONObject jsonArrayObj = jsonArrayStr == null ? new JSONObject() : new JSONObject(jsonArrayStr);
            jsonArray = jsonArrayObj.getJSONArray(getString(R.string.sp_previously_accessed_json));
        } catch (JSONException e){
            e.printStackTrace();
        }


        Log.d(TAG, "getPreviouslyAccessed: " + jsonArray);

        if(jsonArray != null){
            for(int i = 0; i < jsonArray.length(); i++) {
                try {
                    Restaurant restaurant = new Restaurant(jsonArray.getString(i));
                    restaurantList.add(restaurant);
                } catch(JSONException e){
                    e.printStackTrace();
                    Log.e(TAG, "getPreviouslyAccessed: failed to restore restaurant " + i);
                }
            }
        }

        Log.d(TAG, "getPreviouslyAccessed: " + restaurantList);

        return restaurantList;
    }

    /**
     * get the google place url based on the values passed
     */
    private String getUrl(double latitude, double longitude) {
        StringBuilder googlePlaceUrl = new StringBuilder("https://maps.googleapis.com/maps/api/place/textsearch/json?");

        googlePlaceUrl.append("location=").append(latitude).append(",").append(longitude);
        googlePlaceUrl.append("&radius=").append(distance);

        if (!foodType.equals("any")) googlePlaceUrl.append("&query=").append(foodType);
        googlePlaceUrl.append("&type=restaurant");

        googlePlaceUrl.append("&field=formatted_address,name,permanently_closed,place_id," +
                "price_level,rating,user_ratings_total");

        googlePlaceUrl.append("&key=").append(getResources().getString(R.string.google_maps_key));

        Log.d(TAG, "getUrl: " + googlePlaceUrl.toString());
        return googlePlaceUrl.toString();
    }

    public void goToWebsite(View view){
        Uri uriUrl = Uri.parse(activeCard.getURL());
        Intent launchBrowser = new Intent(Intent.ACTION_VIEW, uriUrl);
        startActivity(launchBrowser);
    }

    public void callNumber(View view){
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(Uri.parse("tel:" + activeCard.getPhoneNumber()));
        startActivity(intent);
    }

    public void openMap(View view){
        String map = "http://maps.google.co.in/maps?q=" + activeCard.getAddress();
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(map));
        startActivity(i);
    }

    public void swipeCard(View view) {
        activeCard.swipeCard();
    }

    public void toggleContents(View view) {
        if (view.getId() == R.id.btn_close_contents) {
            activeCard.closeContents();
        } else if (view.getId() == R.id.btn_open_contents) {
            activeCard.openContents();
        }
    }

    /**
     * finish activity and return back to preferences
     */
    public void finishCardFinder(View view) {
        finish();
    }

    @Override
    public void onStop(){
        savePreviouslyAccessedData();
        super.onStop();
    }

    private void showLoadingScreen(){
        loadingView.setVisibility(View.VISIBLE);
    }

    private void hideLoadingScreen(){
        loadingView.setVisibility(View.GONE);
    }

    private void deactivateFloatingButtons(){
        Log.d(TAG, "deactivateFloatingButtons: deactivating buttons");
        btnSwipe.setClickable(false);
        btnBlock.setClickable(false);
        btnOpenContents.setClickable(false);
        btnCloseContents.setClickable(false);

        btnSwipe.hide();
        btnBlock.hide();
        btnOpenContents.hide();
        btnCloseContents.hide();
    }

    private void activateFloatingButtons(){
        Log.d(TAG, "activateFloatingButtons: activating buttons");
        btnSwipe.setClickable(true);
        btnSwipe.show();

        btnBlock.setClickable(true);
        btnBlock.show();

        if(activeCard == null || !activeCard.isContentsVisible()){
            btnOpenContents.setClickable(true);
            btnOpenContents.show();
        }else {
            btnCloseContents.setClickable(true);
            btnCloseContents.show();
        }
    }

    private void hideCards() {
        restCard1.setVisibility(View.INVISIBLE);
        restCard2.setVisibility(View.INVISIBLE);
    }

    /**
     * Animation for a card to move from in card to out of screen
     */
    static Animation outToLeftAnimation() {
        Animation outToLeft = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, -1.0f,
                Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, 0.0f);
        outToLeft.setDuration(200);
        outToLeft.setInterpolator(new AccelerateInterpolator());
        return outToLeft;
    }

    /**
     * Animation for a card to move from out of screen to into screen
     */
    private static Animation inFromRightAnimation() {
        Animation inFromRight = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, +1.0f,
                Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, 0.0f);
        inFromRight.setDuration(400);
        inFromRight.setInterpolator(new AccelerateInterpolator());
        return inFromRight;
    }

    /**
     * logs all of the nearbyPlacesList's HashMaps' name fields for debugging purposes
     */
    private void logAllPlacesList(List<Restaurant> restaurants) {
        Log.d(TAG, "logAllPlacesList: printing nearbyPlacesList---------------------");
        for (Restaurant restaurant: restaurants) {
            Log.d(TAG, "logAllPlacesList: restaurant name=" + restaurant.getName());
        }
        Log.d(TAG, "logAllPlacesList: -----------------------------------------------");
    }
}
