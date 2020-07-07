/*
 * Copyright (c) 2020 Logical Clocks AB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and limitations under the License.
 */
package com.logicalclocks.hsfs.engine;

import com.logicalclocks.hsfs.FeatureStoreException;
import com.logicalclocks.hsfs.TrainingDataset;
import com.logicalclocks.hsfs.metadata.TrainingDatasetApi;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SaveMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;

public class TrainingDatasetEngine {

  private TrainingDatasetApi trainingDatasetApi = new TrainingDatasetApi();
  private Utils utils = new Utils();

  private static final Logger LOGGER = LoggerFactory.getLogger(TrainingDatasetEngine.class);

  //TODO:
  //      Compute statistics

  /**
   * Make a REST call to Hopsworks to create the metadata and write the data on the File System
   * @param trainingDataset
   * @param dataset
   * @param userWriteOptions
   * @throws FeatureStoreException
   * @throws IOException
   */
  public void save(TrainingDataset trainingDataset, Dataset<Row> dataset,
                     Map<String, String> userWriteOptions)
      throws FeatureStoreException, IOException {
    // TODO(Fabio): make sure we can implement the serving part as well
    trainingDataset.setFeatures(utils.parseSchema(dataset));

    // Make the rest call to create the training dataset metadata
    TrainingDataset apiTD = trainingDatasetApi.createTrainingDataset(trainingDataset);

    if (trainingDataset.getVersion() == null) {
      LOGGER.info("VersionWarning: No version provided for creating training dataset `" + trainingDataset.getName() +
        "`, incremented version to `" + apiTD.getVersion() + "`.");
    }

    // Update the original object - Hopsworks returns the full location and incremented version
    trainingDataset.setLocation(apiTD.getLocation());
    trainingDataset.setVersion(apiTD.getVersion());

    // Build write options map
    Map<String, String> writeOptions =
        SparkEngine.getInstance().getWriteOptions(userWriteOptions, trainingDataset.getDataFormat());

    SparkEngine.getInstance().write(trainingDataset, dataset, writeOptions, SaveMode.Overwrite);
  }

  /**
   * Insert (append or overwrite) data on a training dataset
   * @param trainingDataset
   * @param dataset
   * @param providedOptions
   * @param saveMode
   * @throws FeatureStoreException
   */
  public void insert(TrainingDataset trainingDataset, Dataset<Row> dataset,
                     Map<String, String> providedOptions, SaveMode saveMode)
      throws FeatureStoreException {
    // validate that the schema matches
    utils.schemaMatches(dataset, trainingDataset.getFeatures());

    Map<String, String> writeOptions =
        SparkEngine.getInstance().getWriteOptions(providedOptions, trainingDataset.getDataFormat());

    SparkEngine.getInstance().write(trainingDataset, dataset, writeOptions, saveMode);
  }

  public Dataset<Row> read(TrainingDataset trainingDataset, String split, Map<String, String> providedOptions) {
    if (trainingDataset.getStorageConnector() != null) {
      SparkEngine.getInstance().configureConnector(trainingDataset.getStorageConnector());
    }

    String path = "";
    if (com.google.common.base.Strings.isNullOrEmpty(split)) {
      // ** glob means "all sub directories"
      // TODO(Fabio): make sure it works on S3
      path = Paths.get(trainingDataset.getLocation(), "**").toString();
    } else {
      path = Paths.get(trainingDataset.getLocation(), split).toString();
    }

    Map<String, String> readOptions =
        SparkEngine.getInstance().getReadOptions(providedOptions, trainingDataset.getDataFormat());
    return SparkEngine.getInstance().read(trainingDataset.getDataFormat(), readOptions, path);
  }

}