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

import android.os.AsyncTask;
import android.util.Log;

import com.google.api.client.http.InputStreamContent;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.StorageObject;

import java.io.IOException;
import java.io.InputStream;

/**
 *
 */
public class AbelanaUpload extends AsyncTask<Void, Void, Void> {
    static final String TAG = "Upload";

    private static final String BUCKET = "abelana-in";

    private InputStream file_to_copy;
    private String fileName;

    public AbelanaUpload(InputStream file, String name) {

        file_to_copy = file;
        fileName = name;

        Log.i(TAG,"b4 execute");
        execute();
    }

    @Override
    protected Void doInBackground(Void... params) {

        try {
            InputStreamContent mediaContent = new InputStreamContent( "application/octet-stream", file_to_copy);
            // Not strictly necessary, but allows optimization in the cloud.
            // mediaContent.setLength(OBJECT_SIZE);

            StorageObject objectMetadata = null;

            Storage.Objects.Insert insertObject =
                    AbelanaThings.storage.objects().insert(BUCKET, objectMetadata, mediaContent);

            // If you don't provide metadata, you will have specify the object
            // name by parameter. You will probably also want to ensure that your
            // default object ACLs (a bucket property) are set appropriately:
            // https://developers.google.com/storage/docs/json_api/v1/buckets#defaultObjectAcl
            insertObject.setName( fileName );

            insertObject.getMediaHttpUploader().setDisableGZipContent(true);
            // For small files, you may wish to call setDirectUploadEnabled(true), to
            // reduce the number of HTTP requests made to the server.
            if (mediaContent.getLength() > 0 && mediaContent.getLength() <= 2 * 1000 * 1000 /* 2MB */) {
                insertObject.getMediaHttpUploader().setDirectUploadEnabled(true);
            }
            insertObject.execute();

        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        super.onPostExecute(aVoid);
        Log.i("Update", "done.");

    }
}
