(ns riemann.config
  "Riemann config files are eval'd in the context of this namespace. Includes
  streams, client, email, logging, and graphite; the common functions used in
  config. Provides a default core and functions ((tcp|udp)-server, streams,
  index) which modify that core."
  (:require [riemann.core :as core])
  (:require [riemann.server])
  (:require riemann.index)
  (:require [riemann.logging :as logging])
  (:require [riemann.folds :as folds])
  (:use clojure.tools.logging)
  (:use riemann.client)
  (:require [riemann.pubsub :as pubsub])
  (:use [riemann.streams :exclude [update-index delete-from-index]])
  (:use riemann.email)
  (:use riemann.graphite)
  (:gen-class))

(def ^{:doc "A default core."} core (core/core))

(defn tcp-server 
  "Add a new TCP server with opts to the default core."
  [& opts]
  (dosync
    (alter (core :servers) conj
      (riemann.server/tcp-server core (apply hash-map opts)))))

(defn udp-server 
  "Add a new UDP server with opts to the default core."
  [& opts]
  (dosync
    (alter (core :servers) conj
      (riemann.server/udp-server core (apply hash-map opts)))))

(defn ws-server
  "Add a new websockets server with opts to the default core."
  [& opts]
  (dosync
    (alter (core :servers) conj
           (riemann.server/ws-server core (apply hash-map opts)))))

(defn streams
  "Add any number of streams to the default core." 
  [& things]
  (dosync
    (alter (core :streams) #(vec (concat %1 %2)) things)))

(defn index 
  "Set the index used by this core."
  [& opts]
  (dosync
    (ref-set (core :index) (apply riemann.index/index opts))))

(defn update-index
  "Updates the given index with all events received. Also publishes to the index pubsub channel."
  [index]
  (fn [event] (core/update-index core event)))

(defn delete-from-index
  "Deletes any events that pass through from the index"
  [index]
  (fn [event] (core/delete-from-index core event)))

(defn periodically-expire
  "Sets up a reaper for this core. See core API docs."
  ([interval]
    (core/periodically-expire core interval))
  ([]
   (periodically-expire 10)))

(defn pubsub
  "Returns this core's pubsub registry."
  []
  (:pubsub core))

(defn publish
  "Returns a stream which publishes events to this the given channel. Uses this
  core's pubsub registry."
  [channel]
  (fn [event]
    (pubsub/publish (:pubsub core) channel event)))

(defn subscribe
  "Subscribes to the given channel with f, which will receive events. Uses this
  core's pubsub registry."
  [channel f]
  (pubsub/subscribe (:pubsub core) channel f))
    
; Start the core
(defn start []
  (core/start core))

(defn include 
  "Include another config file.

  (include \"foo.clj\")"
  [file]
  (let [file (or file (first *command-line-args*) "riemann.config")]
    (binding [*ns* (find-ns 'riemann.config)]
      (load-string (slurp file)))))
