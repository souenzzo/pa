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
    (lu/inject-resolvers {:Element/selectString (fn [_ {:keys [^String of ^String attribute]} {::keys [^Element element]}]
                                                  (if attribute
                                                    (let [^Element el (first (.select element of))]
                                                      (.attr el attribute))
                                                    (string/join ""
                                                      (for [^Element el (.select element of)
                                                            txt (.textNodes el)]
                                                        (str txt)))))
                          :Element/scraper      (fn [{::keys [^HttpClient http-client]} {:keys [url selectParams]} {::keys [^Element element]}]
                                                  (let [url (apply format url
                                                              (for [^String of selectParams
                                                                    :when of]
                                                                (string/join ""
                                                                  (for [^Element el (.select element of)
                                                                        txt (.textNodes el)]
                                                                    (str txt)))))

                                                        req (.build (HttpRequest/newBuilder (URI/create url)))
                                                        res (.send http-client
                                                              req
                                                              (HttpResponse$BodyHandlers/ofInputStream))
                                                        body ^InputStream (.body res)]
                                                    {:url      url
                                                     ::body    body
                                                     ::element (Jsoup/parse body (str StandardCharsets/UTF_8) (str url))
                                                     ::headers (into {} (.map (.headers res)))
                                                     ::status  (.statusCode res)}))
                          :Element/selectList   (fn [_ {:keys [^String of]} {::keys [^Element element]
                                                                             :as    input}]
                                                  (for [^Element el (.select element of)]
                                                    (assoc input ::element el)))
                          :Query/hello          (fn [_ _ _]
                                                  "World")
                          :Query/scraper        (fn [{::keys [^HttpClient http-client]} {:keys [url]} _]
                                                  (let [req (.build (HttpRequest/newBuilder (URI/create url)))
                                                        res (.send http-client
                                                              req
                                                              (HttpResponse$BodyHandlers/ofInputStream))
                                                        body ^InputStream (.body res)]
                                                    {:url      url
                                                     ::body    body
                                                     ::element (Jsoup/parse body (str StandardCharsets/UTF_8) (str url))
                                                     ::headers (into {} (.map (.headers res)))
                                                     ::status  (.statusCode res)}))})
    ls/compile))
