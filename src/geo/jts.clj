(ns geo.jts
  "Wrapper for the locationtech JTS spatial library. Constructors for points,
  coordinate sequences, rings, polygons, multipolygons, and so on."
  (:require [clojure.string :as str])
  (:import (org.locationtech.jts.geom Coordinate
                                      CoordinateSequenceFilter
                                      Geometry
                                      Point
                                      LinearRing
                                      LineSegment
                                      LineString
                                      PrecisionModel
                                      Polygon
                                      PrecisionModel
                                      GeometryFactory)
           (org.osgeo.proj4j CoordinateTransformFactory
                             CRSFactory
                             ProjCoordinate)))

(def ^PrecisionModel pm (PrecisionModel. PrecisionModel/FLOATING))

(defn gf
  "Creates a GeometryFactory for a given SRID."
  [srid]
  (GeometryFactory. pm srid))

(def ^GeometryFactory gf-wgs84
  (gf 4326))

(defn get-srid
  "Gets an integer SRID for a given geometry."
  [geom]
  (.getSRID geom))

(defn set-srid
  "Sets a geometry's SRID to a new value, and returns that geometry."
  [geom srid]
  (.setSRID geom srid)
  geom)

(defn get-factory
  "Gets a GeometryFactory for a given geometry."
  [geom]
  (.getFactory geom))

(defn epsg?
  [s]
  (try (and (= (subs s 0 4) "EPSG")
            (int? (read-string (subs s 5))))
       (catch Exception _
         false)))

(defn srid->epsg
  "Converts SRID integer to EPSG string."
  [srid]
  (str/join ["EPSG:" srid]))

(defn epsg->srid
  "Converts EPSG string to SRID, if possible."
  [epsg]
  (cond (int? epsg)
        epsg
        (epsg? epsg)
        (read-string (subs epsg 5))))

(def ^CoordinateTransformFactory ctf-factory
  (CoordinateTransformFactory.))

(def ^CRSFactory crs-factory
  (CRSFactory.))

(defn crs-name?
  "Check if input is a valid CRS name"
  [c]
  (try (or (= (subs c 0 5) "EPSG:")
           (= (subs c 0 5) "ESRI:")
           (= (subs c 0 5) "NA83:")
           (= (subs c 0 6) "WORLD:")
           (= (subs c 0 6) "NAD27:"))
       (catch Exception _
         false)))

(defn proj4-string?
  "Check if input appears to be a proj4 string"
  [c]
  (try (.contains c "+proj=")
       (catch Exception _
         false)))


(defn- create-crs
  "Create a CRS system. If given an integer, assume it is an EPSG code.
  If given a valid CRS name or proj4 string, use that as the CRS identifier."
  [c]
  (cond (int? c)
        (.createFromName crs-factory (srid->epsg c))
        (crs-name? c)
        (.createFromName crs-factory c)
        (proj4-string? c)
        (.createFromParameters crs-factory "" c)))

(defn- create-transform
  "Creates a proj4j transform between two projection systems.
  c1 or c2 can be:
   integers (which will be interpreted as that EPSG);
   a string identifier for types EPSG:XXXX, ESRI:XXXX, WORLD:XXXX, NA83:XXXX, or NAD27:XXXX;
   or a proj4 string."
  [c1 c2]
  (.createTransform ctf-factory
                    (create-crs c1)
                    (create-crs c2)))

(defn coordinate
  "Creates a Coordinate."
  ([x y]
   (Coordinate. x y)))

(defn point
  "Creates a Point from a Coordinate, or an x,y pair. Allows an optional SRID argument at end."
  ([^Coordinate coordinate]
   (.createPoint gf-wgs84 coordinate))
  ([x-or-coordinate y-or-srid]
   (cond (and (number? x-or-coordinate) (number? y-or-srid))
         (point (coordinate x-or-coordinate y-or-srid))
         (and (instance? Coordinate x-or-coordinate)
              (integer? y-or-srid))
         (.createPoint (gf y-or-srid) x-or-coordinate)))
  ([x y srid]
   (point (coordinate x y) srid)))

(defn coordinate-sequence
  "Given a list of Coordinates, generates a CoordinateSequence."
  [coordinates]
  (.. gf-wgs84 getCoordinateSequenceFactory create
      (into-array Coordinate coordinates)))

(defn wkt->coords-array
  [flat-coord-list]
  (->> flat-coord-list
       (partition 2)
       (map (partial apply coordinate))))

(defn linestring
  "Given a list of Coordinates, creates a LineString. Allows an optional SRID argument at end."
  ([coordinates]
   (.createLineString gf-wgs84 (into-array Coordinate coordinates)))
  ([coordinates srid]
   (.createLineString (gf srid) (into-array Coordinate coordinates))))

(defn linestring-wkt
  "Makes a LineString from a WKT-style data structure: a flat sequence of
  coordinate pairs, e.g. [0 0, 1 0, 0 2, 0 0]. Allows an optional SRID argument at end."
  ([coordinates]
   (-> coordinates wkt->coords-array linestring))
  ([coordinates srid]
   (-> coordinates wkt->coords-array (linestring srid))))

(defn coords
  [^LineString linestring]
  (-> linestring .getCoordinateSequence .toCoordinateArray))

(defn coord
  [^Point point]
  (.getCoordinate point))

(defn point-n
  "Get the point for a linestring at the specified index."
  [^LineString linestring idx]
  (.getPointN linestring idx))

(defn segment-at-idx
  "LineSegment from a LineString's point at index to index + 1."
  [^LineString linestring idx]
  (LineSegment. (coord (point-n linestring idx))
                (coord (point-n linestring (inc idx)))))

(defn linear-ring
  "Given a list of Coordinates, creates a LinearRing. Allows an optional SRID argument at end."
  ([coordinates]
   (.createLinearRing gf-wgs84 (into-array Coordinate coordinates)))
  ([coordinates srid]
   (.createLinearRing (gf srid) (into-array Coordinate coordinates))))

(defn linear-ring-wkt
  "Makes a LinearRing from a WKT-style data structure: a flat sequence of
  coordinate pairs, e.g. [0 0, 1 0, 0 2, 0 0]. Allows an optional SRID argument at end."
  ([coordinates]
   (-> coordinates wkt->coords-array linear-ring))
  ([coordinates srid]
   (-> coordinates wkt->coords-array (linear-ring srid))))

(defn polygon
  "Given a LinearRing shell, and a list of LinearRing holes, generates a
  polygon."
  ([shell]
   (polygon shell nil))
  ([shell holes]
   (.createPolygon (get-factory shell) shell (into-array LinearRing holes))))

(defn polygon-wkt
  "Generates a polygon from a WKT-style data structure: a sequence of
  [outer-ring hole1 hole2 ...], where outer-ring and each hole is a flat list
  of coordinate pairs, e.g.

  [[0 0 10 0 10 10 0 0]
   [1 1  9 1  9  9 1 1]].

   Allows an optional SRID argument at end."
  ([rings]
   (let [rings (map linear-ring-wkt rings)]
     (polygon (first rings) (into-array LinearRing (rest rings)))))
  ([rings srid]
   (let [rings (map #(linear-ring-wkt % srid) rings)]
     (polygon (first rings) (into-array LinearRing (rest rings))))))

(defn multi-polygon
  "Given a list of polygons, generates a MultiPolygon."
  [polygons]
  (.createMultiPolygon (get-factory (first polygons)) (into-array Polygon polygons)))

(defn multi-polygon-wkt
  "Creates a MultiPolygon from a WKT-style data structure, e.g. [[[0 0 1 0 2 2
  0 0]] [5 5 10 10 6 2]]. Allows an optional SRID argument at end."
  ([wkt]
   (multi-polygon (map polygon-wkt wkt)))
  ([wkt srid]
   (multi-polygon (map #(polygon-wkt % srid) wkt))))

(defn- coord-dimension-check
  "Check if a Coordinate is 3D or 2D"
  [coord]
  (let [x (not (Double/isNaN (.x coord)))
        y (not (Double/isNaN (.y coord)))
        z (not (Double/isNaN (.z coord)))]
    (cond (and x y z)
          3
          (and x y)
          2)))

(defn coordinates
  "Get a sequence of Coordinates from a Geometry"
  [^Geometry geom]
  (into [] (.getCoordinates geom)))

(defn geom-dimension-check
  "In a Geometry, return 3 if any Coordinate has a Z.
  Otherwise return 2 if any Coordinate has a Y."
  [geom]
  (let [coords (coordinates geom)
        dimensions (into [] (map coord-dimension-check coords))]
    (cond (some #{3} dimensions)
          3
          (some #{2} dimensions)
          2)))

(defn same-srid?
  "Check if two Geometries have the same SRID."
  [^Geometry g1 ^Geometry g2]
  (and (= (get-srid g1) (get-srid g2))
       (not= (get-srid g1) 0)))

(defn same-coords?
  "Check if two Coordinates have the same values."
  [^Coordinate c1 ^Coordinate c2]
  (let [d1 (coord-dimension-check c1)
        d2 (coord-dimension-check c2)]
    (and (= d1 d2)
         (cond (= d1 3)
               (and (= (.x c1) (.x c2))
                    (= (.y c1) (.y c2))
                    (= (.z c1) (.z c2)))
               (= d1 2)
               (and (= (.x c1) (.x c2))
                    (= (.y c1) (.y c2)))))))

(defn same-geom?
  "Check if each vertex in two Geometries has the same coordinates and SRID."
  [^Geometry g1 ^Geometry g2]
  (let [coords1 (coordinates g1)
        coords2 (coordinates g2)]
    (and (same-srid? g1 g2)
         (= (count coords1) (count coords2)))
    (map #(same-coords? (nth coords1 %) (nth coords2 %)) (range (count coords1)))))

(defn- transform-coord
  "Transforms a coordinate using a proj4j transform.
  Can either be specified with a transform argument or two projection arguments."
  ([coord transform]
   (-> (.transform transform
                   (ProjCoordinate. (.x coord) (.y coord))
                   (ProjCoordinate.))
       (#(coordinate (.x %) (.y %)))))
  ([coord c1 c2]
   (if (= c1 c2) coord
                       (transform-coord coord (create-transform c1
                                                                c2)))))

(defn- transform-coord-seq-item
  "Transforms one item in a CoordinateSequence using a proj4j transform."
  [cseq i transform]
  (let [coordinate (.getCoordinate cseq i)
        transformed (transform-coord coordinate transform)]
    (.setOrdinate cseq i 0 (.x transformed))
    (.setOrdinate cseq i 1 (.y transformed))))

(defn- transform-coord-seq-filter
  "Implement JTS's CoordinateSequenceFilter, to be applied to a Geometry using transform-geom."
  [transform]
  (reify CoordinateSequenceFilter
    (filter [_ seq i]
      (transform-coord-seq-item seq i transform))
    (isDone [_]
      false)
    (isGeometryChanged [_]
      true)))

(defn- tf-set-srid
  "When the final projection for a tf is an SRID or EPSG, set the Geometry's SRID."
  [g c]
  (cond (int? c)
        (set-srid g c)
        (epsg? c)
        (set-srid g (epsg->srid c))
        :else
        g))

(defn- tf
  "Transform a Geometry from one CRS to another.
  When the target transformation is an EPSG code, set the Geometry's SRID to that integer."
  [g c1 c2]
  (let [tcsf (transform-coord-seq-filter
               (create-transform c1 c2))]
    (.apply g tcsf)
    (tf-set-srid g c2)))

(defn transform-geom
  "Transform a Geometry using a proj4j transform, if needed. Returns a new Geometry if a transform occurs.
  When only one CRS is given, get the CRS of the existing geometry.
  When two are given, force the transformation to occur between those two systems."
  ([g crs]
   (let [geom-srid (get-srid g)]
     (cond (= geom-srid 0)
           (Exception. "Geometry does not have an SRID")
           (or (= geom-srid crs)
               (= (srid->epsg geom-srid) crs))
           g
           :else
           (transform-geom g geom-srid crs))))
  ([g crs1 crs2]
   (-> (if (= crs1 crs2)
         (tf-set-srid g crs2)
         (tf (.clone g) crs1 crs2)))))