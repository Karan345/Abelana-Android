/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.samples.apps.abelana;


import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.Toast;

import com.google.identitytoolkit.IdToken;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.ArrayList;

import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

/**
 * Fragment representing the photo feed, which is the Home tab of the app
 */
public class FeedFragment extends Fragment {
    private static final int REQUEST_IMAGE_CAPTURE = 0;
    private static final int REQUEST_IMAGE_CHOOSE = 1;
    private static final int MEDIA_TYPE_IMAGE = 3;
    private Uri mMediaUri;
    private File mPhotoFile;
    private final String LOG_TAG = FeedFragment.class.getSimpleName();

    public FeedFragment() {
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.feed, menu);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_feed, container, false);
        //required to display menu with the camera button
        setHasOptionsMenu(true);
        final ListView listView = (ListView) rootView.findViewById(R.id.listview_timeline);

        /* Instantiate the class which has interfaces for the REST APIs. API calls happen off the
         * UI thread in order to not halt the app. The callback function handles the API response.
         * The response contains the information for the feed, including photos, names of people
         * who took the photos, number of likes, and whether the current user liked the photo. This
         * information is stored in the Data class and is accessed by the FeedAdapter to display it.
         */
        AbelanaClient client = new AbelanaClient();
        client.mTimeline.timeline(Data.aTok, "0", new Callback<AbelanaClient.Timeline>() {
            @Override
            public void success(AbelanaClient.Timeline timelineResponse, Response response) {
                Data.mFeedIds = new ArrayList<String>();
                Data.mLikes = new ArrayList<Integer>();
                Data.mNames = new ArrayList<String>();
                Data.mILike = new ArrayList<Boolean>();
                Data.mFeedUrls = new ArrayList<String>();
                String qualifier = rootView.getResources().getString(R.string.size_qualifier);
                if (timelineResponse.entries != null) {
                    for (AbelanaClient.TimelineEntry e : timelineResponse.entries) {
                        Data.mFeedIds.add(e.photoid);
                        Data.mFeedUrls.add(AbelanaThings.getImage(e.photoid + qualifier));
                        Data.mLikes.add(e.likes);
                        Data.mNames.add(e.name);
                        Data.mILike.add(e.ilike);
                    }
                }
                //Warm the image cache by pre-loading the next 5 photos
                int count = 0;
                for (String url: Data.mFeedUrls) {
                    Picasso.with(getActivity()).load(url).into(new Target() {
                        @Override
                        public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                            // cache is now warmed up
                        }

                        @Override
                        public void onBitmapFailed(Drawable errorDrawable) {
                        }

                        @Override
                        public void onPrepareLoad(Drawable placeHolderDrawable) {
                        }
                    });

                    count++;
                    if (count == 5) break;
                }
                //set the adapter for the feed listview
                listView.setAdapter(new FeedAdapter(getActivity()));
            }

            @Override
            public void failure(RetrofitError error) {
                error.printStackTrace();
            }
        });


        return rootView;
    }

    // Lifecycle method to handle menu option actions
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //Allows the user to choose whether to take a new photo, or choose a photo from the Gallery
        if (id == R.id.action_camera) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setItems(R.array.camera_choices, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int position) {
                    if (position == REQUEST_IMAGE_CAPTURE) {
                        takePicture();
                    } else if (position == REQUEST_IMAGE_CHOOSE) {
                        Intent choosePhotoIntent = new Intent(Intent.ACTION_GET_CONTENT);
                        choosePhotoIntent.addCategory(Intent.CATEGORY_OPENABLE);
                        choosePhotoIntent.setType("image/*");
                        startActivityForResult(choosePhotoIntent, REQUEST_IMAGE_CHOOSE);
                    }
                }
            });

            AlertDialog dialog = builder.create();
            dialog.show();
        }
        //Refreshes the feed by re-loading the freed fragment (which in turn calls the API for new data)
        if (id == R.id.action_refresh) {
            FragmentTransaction transaction = getFragmentManager().beginTransaction();
            transaction.replace(R.id.content_frame, new FeedFragment())
                    .commit();
        }

        return super.onOptionsItemSelected(item);

    }

    private void takePicture() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        //file to save in the cloud
        mPhotoFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);
        //Uri to store the photo locally in the Android Gallery
        mMediaUri = Uri.fromFile(mPhotoFile);
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, mMediaUri);
        if (takePictureIntent.resolveActivity(getActivity().getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    //Lifecycle method called after a photo is taken or an existing image is selected by the user
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_IMAGE_CAPTURE) {
                //add the photo to the Gallery
                Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                mediaScanIntent.setData(mMediaUri);
                getActivity().sendBroadcast(mediaScanIntent);
                Log.v(LOG_TAG, "Photo is called " + mPhotoFile.getName());

                //upload the photo to the cloud
                try {
                    InputStream photoStream = new FileInputStream(mPhotoFile);
                    new AbelanaUpload(photoStream, mPhotoFile.getName());
                    Toast.makeText(getActivity(),
                            getActivity().getString(R.string.photo_upload_success_message), Toast.LENGTH_SHORT)
                            .show();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            } else if (requestCode == REQUEST_IMAGE_CHOOSE) {
                Uri photoUri = data.getData();
                try {
                    InputStream photoStream = getActivity().getContentResolver().openInputStream(photoUri);
                    String fileName = generateFileName() + ".jpg";
                    new AbelanaUpload(photoStream, fileName);
                    Toast.makeText(getActivity(),
                            getActivity().getString(R.string.photo_upload_success_message), Toast.LENGTH_SHORT)
                            .show();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
        else if (resultCode != Activity.RESULT_CANCELED) {
            Toast.makeText(getActivity(), "error!", Toast.LENGTH_LONG).show();
        }
    }

    /** Create a File for saving an image or video */
    private File getOutputMediaFile(int type){
        String appName = getActivity().getString(R.string.app_name);
        if (isExternalStorageAvailable()) {
            // To be safe, you should check that the SDCard is mounted
            // using Environment.getExternalStorageState() before doing this.

            File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), appName);
            // This location works best if you want the created images to be shared
            // between applications and persist after your app has been uninstalled.

            // Create the storage directory if it does not exist
            if (! mediaStorageDir.exists()){
                if (! mediaStorageDir.mkdirs()){
                    Log.d(appName, "failed to create directory");
                    return null;
                }
            }

                /* Create a media file name*/
            String fileName = generateFileName();

            Log.v(LOG_TAG, "File name is " + fileName);

            File mediaFile;
            if (type == MEDIA_TYPE_IMAGE){
                mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                        fileName + ".jpg");
                Log.d(LOG_TAG, "File: " + Uri.fromFile(mediaFile));
                return mediaFile;
            } else {
                return null;
            }
        } else {
            return null;
        }

    }

    private String generateFileName() {
        //Get localID
        UserInfoStore client = new UserInfoStore(getActivity());
        IdToken token = client.getSavedIdToken();
        String localId = token.getLocalId();

        //Generate 8 digit random number and encode it in base64
        SecureRandom rand = new SecureRandom();

        StringBuilder rand8Dig = new StringBuilder(8);
        for (int i=0; i < 8; i++) {
            rand8Dig.append((char)('0' + rand.nextInt(10)));
        }
        String randNum = rand8Dig.toString();

        String randNumB64 = Utilities.base64Encoding(randNum);

        String fileName = localId + '.' + randNumB64;

        fileName += 'P'; //'P' marks that a photo is public, future versions will allow for private photos

        return fileName;
    }

    private boolean isExternalStorageAvailable() {
        String state = Environment.getExternalStorageState();

        if (state.equals(Environment.MEDIA_MOUNTED)) {
            return true;
        } else {
            return false;
        }
    }
}
