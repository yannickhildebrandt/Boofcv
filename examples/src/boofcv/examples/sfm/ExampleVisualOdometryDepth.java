/*
 * Copyright (c) 2011-2017, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package boofcv.examples.sfm;

import boofcv.abst.feature.detect.interest.ConfigGeneralDetector;
import boofcv.abst.feature.tracker.PointTrackerTwoPass;
import boofcv.abst.sfm.AccessPointTracks3D;
import boofcv.abst.sfm.d3.DepthVisualOdometry;
import boofcv.abst.sfm.d3.VisualOdometry;
import boofcv.alg.sfm.DepthSparse3D;
import boofcv.alg.tracker.klt.PkltConfig;
import boofcv.factory.feature.tracker.FactoryPointTrackerTwoPass;
import boofcv.factory.sfm.FactoryVisualOdometry;
import boofcv.io.MediaManager;
import boofcv.io.UtilIO;
import boofcv.io.calibration.CalibrationIO;
import boofcv.io.image.SimpleImageSequence;
import boofcv.io.wrapper.DefaultMediaManager;
import boofcv.struct.calib.VisualDepthParameters;
import boofcv.struct.distort.DoNothing2Transform2_F32;
import boofcv.struct.image.GrayS16;
import boofcv.struct.image.GrayU16;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageType;
import georegression.struct.point.Vector3D_F64;
import georegression.struct.se.Se3_F64;

import java.io.IOException;

/**
 * Bare bones example showing how to estimate the camera's ego-motion using a depth camera system, e.g. Kinect.
 * Additional information on the scene can be optionally extracted from the algorithm if it implements AccessPointTracks3D.
 *
 * @author Peter Abeles
 */
public class ExampleVisualOdometryDepth {

	public static void main( String args[] ) throws IOException {

		MediaManager media = DefaultMediaManager.INSTANCE;

		String directory = UtilIO.pathExample("kinect/straight");

		// load camera description and the video sequence
		VisualDepthParameters param = CalibrationIO.load(media.openFile(directory + "visualdepth.yaml"));

		// specify how the image features are going to be tracked
		PkltConfig configKlt = new PkltConfig();
		configKlt.pyramidScaling = new int[]{1, 2, 4, 8};
		configKlt.templateRadius = 3;

		PointTrackerTwoPass<GrayU8> tracker =
				FactoryPointTrackerTwoPass.klt(configKlt, new ConfigGeneralDetector(600, 3, 1),
						GrayU8.class, GrayS16.class);

		DepthSparse3D<GrayU16> sparseDepth = new DepthSparse3D.I<>(1e-3);

		// declares the algorithm
		DepthVisualOdometry<GrayU8,GrayU16> visualOdometry =
				FactoryVisualOdometry.depthDepthPnP(1.5, 120, 2, 200, 50, true,
				sparseDepth, tracker, GrayU8.class, GrayU16.class);

		// Pass in intrinsic/extrinsic calibration.  This can be changed in the future.
		visualOdometry.setCalibration(param.visualParam,new DoNothing2Transform2_F32());

		// Process the video sequence and output the location plus number of inliers
		SimpleImageSequence<GrayU8> videoVisual = media.openVideo(directory+"rgb.mjpeg", ImageType.single(GrayU8.class));
		SimpleImageSequence<GrayU16> videoDepth = media.openVideo(directory + "depth.mpng", ImageType.single(GrayU16.class));

		while( videoVisual.hasNext() ) {
			GrayU8 visual = videoVisual.next();
			GrayU16 depth = videoDepth.next();

			if( !visualOdometry.process(visual,depth) ) {
				throw new RuntimeException("VO Failed!");
			}

			Se3_F64 leftToWorld = visualOdometry.getCameraToWorld();
			Vector3D_F64 T = leftToWorld.getT();

			System.out.printf("Location %8.2f %8.2f %8.2f      inliers %s\n", T.x, T.y, T.z, inlierPercent(visualOdometry));
		}
	}

	/**
	 * If the algorithm implements AccessPointTracks3D, then count the number of inlier features
	 * and return a string.
	 */
	public static String inlierPercent(VisualOdometry alg) {
		if( !(alg instanceof AccessPointTracks3D))
			return "";

		AccessPointTracks3D access = (AccessPointTracks3D)alg;

		int count = 0;
		int N = access.getAllTracks().size();
		for( int i = 0; i < N; i++ ) {
			if( access.isInlier(i) )
				count++;
		}

		return String.format("%%%5.3f", 100.0 * count / N);
	}
}
