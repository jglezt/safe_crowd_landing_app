package com.parrot.sdksample.view;

import android.graphics.Bitmap;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.mapbox.services.commons.geojson.Polygon;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvException;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class LandingSite {
    private Mat densityArray;
    private int originalRows;
    private int originalColumns;

    public LandingSite(Bitmap densityArray, int originalWidth, int originalHeight){
        Mat tmp = new Mat();
        this.densityArray = new Mat();
        Bitmap bmp32 = densityArray.copy(Bitmap.Config.ARGB_8888, true);
        Utils.bitmapToMat(bmp32, tmp);
        Imgproc.cvtColor(tmp, this.densityArray, Imgproc.COLOR_RGBA2GRAY);

        this.originalColumns = originalWidth;
        this.originalRows = originalHeight;
    }

    private Mat getNegativeDensityMap(Mat densityArray){
        Mat negativeDensityArray = new Mat();
        Core.bitwise_not(densityArray, negativeDensityArray);
        return negativeDensityArray;
    }

    private Mat saturatePixelsMaxRange(Mat densityArray){
        for (int i=0; i<densityArray.rows(); i++)
        {
            for (int j=0; j<densityArray.cols(); j++)
            {
                double[] data = densityArray.get(i, j); //Stores element in an array
                if(data[0] > 0)
                    data[0] = 255;
                densityArray.put(i, j, data); //Puts element back into matrix
            }
        }

        return densityArray;
    }

    private Mat dilateDensityMap(Mat densityArray){
        Mat kernel = new Mat(5, 5, CvType.CV_8UC1, new Scalar(1));

        Mat tmp = new Mat();
        Imgproc.dilate(densityArray, tmp, kernel);
        return tmp;
    }

    private Mat resizeDensityMap(Mat densityArray){
        Mat tmp = new Mat();

        Imgproc.resize(densityArray, tmp, new Size(this.originalColumns, this.originalRows));

        return tmp;
    }

    private Polygon getPolygon(Mat densityArray){
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();

        Imgproc.findContours(densityArray, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        int pointN = 0;
        for(MatOfPoint contour : contours){
            pointN += contour.toList().size();
        }

        double[][][] coordinates = new double[1][pointN][2];

        int pointPosition = 0;

        for(MatOfPoint contour:contours){
            for(Point p : contour.toList()){
                coordinates[0][pointPosition][0] = p.x;
                coordinates[0][pointPosition][1] = p.y;
                pointPosition += 1;
            }
        }

        Polygon polygon = Polygon.fromCoordinates(coordinates);

        return polygon;
    }

    private Polylabel.Cell getSafeZoneCircle(Polygon polygon){
        Polylabel.Cell circle = Polylabel.polylabel(polygon, 1);
        return circle;
    }

    public Bitmap getSafeZoneBitMap(){

        this.densityArray =
                this.saturatePixelsMaxRange(this.densityArray);

        this.densityArray = this.dilateDensityMap(this.densityArray);

        this.densityArray = this.getNegativeDensityMap(this.densityArray);

        this.densityArray = this.resizeDensityMap(this.densityArray);

        Polygon polygon = this.getPolygon(this.densityArray);

        Polylabel.Cell circle = this.getSafeZoneCircle(polygon);

        Log.d("TAG", String.valueOf(String.format("%f %f %f", circle.getX(), circle.getY(), circle.getD())));

        Mat tmp = new Mat (this.densityArray.rows(), this.densityArray.cols(), CvType.CV_8UC4, new Scalar(4));
        Bitmap bmp = null;
        try{
            Imgproc.cvtColor(this.densityArray, tmp, Imgproc.COLOR_GRAY2RGBA);
            Imgproc.circle (
                    tmp,                 //Matrix obj of the image
                    new Point(circle.getX(), circle.getY()),    //Center of the circle
                    (int) circle.getD(),                    //Radius
                    new Scalar(0, 255, 0, 255),//Thickness of the circle
                    Imgproc.FILLED
            );

            bmp = Bitmap.createBitmap(this.densityArray.cols(),this.densityArray.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(tmp, bmp);
        }
        catch (CvException e){
            Log.d("Exception",e.getMessage());}

        return bmp;
    }
}
