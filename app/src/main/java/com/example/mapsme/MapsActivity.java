package com.example.mapsme;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import android.content.Intent;
import android.content.IntentSender;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.TypeFilter;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FetchPlaceResponse;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsResponse;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.mancj.materialsearchbar.MaterialSearchBar;
import com.mancj.materialsearchbar.adapter.SuggestionsAdapter;
import com.skyfishjy.library.RippleBackground;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {
    private GoogleMap mMap;
    private FusedLocationProviderClient mFusedLocationProviderClient;
    private PlacesClient placesClient;
    private List<AutocompletePrediction> predictionList;

    private Location mLastKnownLocation;
    private LocationCallback locationCallback;

    private MaterialSearchBar materialSearchBar;
    private View mapView;
    private Button btnFind;
    private RippleBackground rippleBg;

    private final float DEFAULT_ZOOM = 18;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        materialSearchBar = findViewById(R.id.searchBar);
        btnFind = findViewById(R.id.btn_find);
        rippleBg = findViewById(R.id.ripple_bg);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        mapView = mapFragment.getView();//lưu maps vào view

        //khởi tạo fused location
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(MapsActivity.this);
        //khởi tạo SDK places
        Places.initialize(MapsActivity.this, getString(R.string.google_maps_key));
        //tạo instance vị trí của client
        placesClient = Places.createClient(this);
        final AutocompleteSessionToken token = AutocompleteSessionToken.newInstance();

        materialSearchBar.setOnSearchActionListener(new MaterialSearchBar.OnSearchActionListener() {
            @Override
            public void onSearchStateChanged(boolean enabled) {

            }

            @Override
            public void onSearchConfirmed(CharSequence text) {
                startSearch(text.toString(), true, null, true);
            }

            @Override
            public void onButtonClicked(int buttonCode) {
                if (buttonCode == MaterialSearchBar.BUTTON_NAVIGATION) {
                    //opening or closing a navigation drawer
                } else if (buttonCode == MaterialSearchBar.BUTTON_BACK) {
                    materialSearchBar.disableSearch();
                }
            }
        });
        materialSearchBar.addTextChangeListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                FindAutocompletePredictionsRequest predictionsRequest = FindAutocompletePredictionsRequest.builder()
                        //.setCountry("vn") //có thể thiết lập tìm kiễm tại mỗi quốc gia
                        .setTypeFilter(TypeFilter.ADDRESS)
                        .setSessionToken(token)
                        .setQuery(s.toString())
                        .build();
                placesClient.findAutocompletePredictions(predictionsRequest).addOnCompleteListener(new OnCompleteListener<FindAutocompletePredictionsResponse>() {
                    @Override
                    public void onComplete(@NonNull Task<FindAutocompletePredictionsResponse> task) {
                        if (task.isSuccessful()) {
                            FindAutocompletePredictionsResponse predictionsResponse = task.getResult();
                            if (predictionsResponse != null) {
                                predictionList = predictionsResponse.getAutocompletePredictions();
                                List<String> suggestionsList = new ArrayList<>();
                                for (int i = 0; i < predictionList.size(); i++) {
                                    AutocompletePrediction prediction = predictionList.get(i);
                                    suggestionsList.add(prediction.getFullText(null).toString());
                                }
                                materialSearchBar.updateLastSuggestions(suggestionsList);
                                if (!materialSearchBar.isSuggestionsVisible()) {
                                    materialSearchBar.showSuggestionsList();
                                }
                            }
                        } else {
                            Log.i("mytag", "prediction fetching task unsuccessful");
                        }
                    }
                });
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        //khi click vào địa điểm được tìm kiếm
        materialSearchBar.setSuggstionsClickListener(new SuggestionsAdapter.OnItemViewClickListener() {
            @Override
            public void OnItemClickListener(int position, View v) {
                if (position >= predictionList.size()) {
                    return;
                }
                AutocompletePrediction selectedPrediction = predictionList.get(position);
                String suggestion = materialSearchBar.getLastSuggestions().get(position).toString();
                materialSearchBar.setText(suggestion);

                //khi chọn xong địa điểm thì xóa đi tất cả các chọn lựa khác
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        materialSearchBar.clearSuggestions();
                    }
                }, 1000);

                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(materialSearchBar.getWindowToken(), InputMethodManager.HIDE_IMPLICIT_ONLY);
                }
                String placeId = selectedPrediction.getPlaceId();
                List<Place.Field> placeField = Arrays.asList(Place.Field.LAT_LNG);

                FetchPlaceRequest fetchPlaceRequest = FetchPlaceRequest.builder(placeId, placeField).build();
                placesClient.fetchPlace(fetchPlaceRequest).addOnSuccessListener(new OnSuccessListener<FetchPlaceResponse>() {
                    @Override
                    public void onSuccess(FetchPlaceResponse fetchPlaceResponse) {
                        Place place = fetchPlaceResponse.getPlace();
                        Log.i("mytag", "Place found: " + place.getName());
                        LatLng latLngOfPlace = place.getLatLng();
                        if (latLngOfPlace != null) {
                            mMap.addMarker(new MarkerOptions()
                                    .position(latLngOfPlace)
                                    .title(materialSearchBar.getText()));
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLngOfPlace, DEFAULT_ZOOM));
                        }
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        if (e instanceof ApiException) {
                            ApiException apiException = (ApiException) e;
                            apiException.printStackTrace();
                            int statusCode = apiException.getStatusCode();
                            Log.i("mytag", "place not found: " + e.getMessage());
                            Log.i("mytag", "status code: " + statusCode);
                        }
                    }
                });
            }

            @Override
            public void OnItemDeleteListener(int position, View v) {

            }
        });
        btnFind.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LatLng currentMarkerLocation = mMap.getCameraPosition().target;
                rippleBg.startRippleAnimation();
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        rippleBg.stopRippleAnimation();
                        //startActivities(new Intent(MapsActivity.this, MainActivity.class));
                        //finish();
                    }
                }, 2000);
            }
        });

//        //khởi tạo fused location
//        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
//        if (ActivityCompat.checkSelfPermission(MapsActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
//            //Khi được phép sẽ gọi phương thức
//            getCurrenLocation();
//        } else {
//            //khi permission denied thì yêu cầu permistion
//            ActivityCompat.requestPermissions(MapsActivity.this,
//                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 44);
//        }

    }

//    private void getCurrenLocation() {
//        //khởi tạo vị trí chỉ định (task location)
//        Task<Location> task = mFusedLocationProviderClient.getLastLocation();
//        task.addOnSuccessListener(new OnSuccessListener<Location>() {
//            @Override
//            public void onSuccess(final Location location) {
//                //khi hoàn thành
//                if (location != null) {
//                    //tiến thành đồng bộ trên maps
//                    mapFragment.getMapAsync(new OnMapReadyCallback() {
//                        @Override
//                        public void onMapReady(GoogleMap googleMap) {
//                            //khởi tạo kinh độ vĩ độ (Lat Lng)
//                            LatLng latLng = new LatLng(location.getLatitude(),
//                                    location.getLongitude());
//                            //tạo tùy chọn đánh dấu (marker option)
//                            MarkerOptions options = new MarkerOptions().position(latLng)
//                                    .title("I am there");
//                            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM));
//                            //thêm marker trên map
//                            googleMap.addMarker(options);
//                        }
//                    });
//                }
//            }
//        });
//    }
//
//    //yêu cầu quên truy cập vị trí khi khởi chạy ứng dụng
//    private void requestPermisstion() {
//        Dexter.withContext(this)
//                .withPermissions(Arrays.asList(Manifest.permission.ACCESS_FINE_LOCATION,
//                        Manifest.permission.ACCESS_COARSE_LOCATION))
//                .withListener(new MultiplePermissionsListener() {
//                    @Override
//                    public void onPermissionsChecked(MultiplePermissionsReport multiplePermissionsReport) {
//
//                    }
//
//                    @Override
//                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest> list, PermissionToken permissionToken) {
//                        Toast.makeText(MapsActivity.this, "You must enable this permission", Toast.LENGTH_SHORT).show();
//                    }
//                }).check();
//    }
//
//    //khi chưa được cấp quyền(permission denied) tiến hành yêu cầu quyền
//    //kết quả trả về sẽ được xử lý ở đây
//    @Override
//    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
//        if (requestCode == 44) {
//            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                //khi được phép gọi phương thức
//                getCurrenLocation();
//            }
//        }
//    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        //lấy vị trí hiện tại của thiết bị, hiển thị button đến vị trí thiết bị
        mMap.setMyLocationEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);

        //thao tác trên biến View mapView. thiết lập vị trí Button hiển thị vị trí của thiết bị
        if (mapView != null && mapView.findViewById(Integer.parseInt("1")) != null) {
            View locationButton = ((View) mapView.findViewById(Integer.parseInt("1")).getParent()).findViewById(Integer.parseInt("2"));
            RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) locationButton.getLayoutParams();
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0);
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
            layoutParams.setMargins(0, 0, 40, 180);
        }

        //kiểm tra GPS đã được bật hay chưa, và yêu cầu người dùng bật lên nếu GPS chưa được bật
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(5000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);

        SettingsClient settingsClient = LocationServices.getSettingsClient(MapsActivity.this);
        Task<LocationSettingsResponse> task = settingsClient.checkLocationSettings(builder.build());
        task.addOnSuccessListener(MapsActivity.this, new OnSuccessListener<LocationSettingsResponse>() {
            @Override
            public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                getDeviceLocation();
            }
        });
        //GPS chưa mở, XL tại đây
        task.addOnFailureListener(MapsActivity.this, new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                if (e instanceof ResolvableApiException) {
                    ResolvableApiException resolvable = (ResolvableApiException) e;
                    try {
                        resolvable.startResolutionForResult(MapsActivity.this, 51);
                        //xl tại hàm onActivityResult thông qua requestcode 51;
                    } catch (IntentSender.SendIntentException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        });
        mMap.setOnMyLocationButtonClickListener(new GoogleMap.OnMyLocationButtonClickListener() {
            @Override
            public boolean onMyLocationButtonClick() {
                if (materialSearchBar.isSuggestionsVisible()) {
                    materialSearchBar.clearSuggestions();
                }
                if (materialSearchBar.isSearchEnabled()) {
                    materialSearchBar.disableSearch();
                }
                return false;
            }
        });
    }

    //check GPS khi chưa được mở
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 51) {
            if (resultCode == RESULT_OK) {
                getDeviceLocation();
            }
        }
    }

    //lấy vị trí hiện tại của thiết bị
    private void getDeviceLocation() {
        mFusedLocationProviderClient.getLastLocation()
                .addOnCompleteListener(new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful()) {
                            mLastKnownLocation = task.getResult();
                            if (mLastKnownLocation != null) {
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(mLastKnownLocation.getLatitude(), mLastKnownLocation.getLongitude()), DEFAULT_ZOOM));
                            } else {
                                LocationRequest locationRequest = LocationRequest.create();
                                locationRequest.setInterval(10000);
                                locationRequest.setFastestInterval(5000);
                                locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
                                locationCallback = new LocationCallback() {
                                    @Override
                                    public void onLocationResult(LocationResult locationResult) {
                                        super.onLocationResult(locationResult);
                                        if (locationResult == null) {
                                            return;
                                        }
                                        mLastKnownLocation = locationResult.getLastLocation();
                                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(mLastKnownLocation.getLatitude(), mLastKnownLocation.getLongitude()), DEFAULT_ZOOM));
                                        mFusedLocationProviderClient.removeLocationUpdates(locationCallback);
                                    }
                                };
                                mFusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null);
                            }
                        } else {
                            Toast.makeText(MapsActivity.this, "unable to get last location", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }
}
