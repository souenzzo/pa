(ns pa.core
  (:require [clojure.string :as string]
            [com.walmartlabs.lacinia.schema :as ls]
            [clojure.java.io :as io]
            [com.walmartlabs.lacinia.util :as lu]
            [com.walmartlabs.lacinia.parser.schema :as lps])
  (:import (java.io InputStream)
           (java.net URI)
           (java.net.http HttpClient HttpRequest HttpResponse$BodyHandlers)
           (java.nio.charset StandardCharsets)
           (org.jsoup Jsoup)
           (org.jsoup.nodes Element Document)))

(set! *warn-on-reflection* true)

(def schema
  (-> "pa.graphql"
    io/resource
    slurp
    lps/parse-schema
    (lu/inject-resolvers {:Document/selectString (fn [_ {:keys [^String cssQuery]} {::keys [^Document document]}]
                                                   (string/join ""
                                                     (for [^Element el (.select document cssQuery)
                                                           txt (.textNodes el)]
                                                       (str txt))))
                          :Query/hello           (fn [_ _ _]
                                                   "World")
                          :Query/scraper         (fn [{::keys [^HttpClient http-client]} {:keys [url]} _]
                                                   (let [req (.build (HttpRequest/newBuilder (URI/create url)))
                                                         res (.send http-client
                                                               req
                                                               (HttpResponse$BodyHandlers/ofInputStream))
                                                         body ^InputStream (.body res)]
                                                     {::body     body
                                                      ::document (Jsoup/parse body (str StandardCharsets/UTF_8) (str url))
                                                      ::headers  (into {} (.map (.headers res)))
                                                      ::status   (.statusCode res)}))})
    ls/compile))
