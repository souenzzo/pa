(ns pa.main
  (:gen-class)
  (:require [io.pedestal.http :as http]
            [com.walmartlabs.lacinia.pedestal2 :as lp]
            [pa.core :as pa])
  (:import (java.net.http HttpClient)))

(defonce state (atom nil))

(defn -main
  [& _]
  (swap! state
    (fn [st]
      (some-> st http/stop)
      (-> (lp/default-service pa/schema {:app-context {::pa/http-client (HttpClient/newHttpClient)}})
        (merge (when-let [port (System/getenv "PORT")]
                 {::http/port (Long/parseLong port)})
          {::http/host "0.0.0.0"})
        http/create-server
        http/start))))
