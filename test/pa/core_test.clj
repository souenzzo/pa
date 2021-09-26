(ns pa.core-test
  (:require [pa.core :as pa]
            [hiccup2.core :as h]
            [clojure.test :refer [deftest is]]
            [clojure.pprint :as pprint]
            [ring.core.protocols]
            [clojure.java.io :as io]
            [com.walmartlabs.lacinia :as lacinia]
            [clojure.string :as string])
  (:import (java.net.http HttpResponse HttpClient HttpHeaders HttpRequest)
           (java.util.function BiPredicate)
           (java.nio.charset StandardCharsets)
           (java.io ByteArrayOutputStream)))
;; https://docs.oracle.com/en/java/javase/17/docs/api/java.xml/javax/xml/xpath/package-summary.html

(set! *warn-on-reflection* true)
#_"
query {
  g1:scraper(url: \"https://g1.globo.com\") {
    title:    selectString(\"tr > div > h1\")
    comments: selectString(\"tr > div > div:nth-child(5) > div\") {
      name: selectString(\"h1\")
      msg: selectString(\"div\")
      twitter: scraper(url:\"https://twitter.com/%s\", selectParams: [\"main > div.twitter\"]) {
        lastTweet: selectString(\"div > main > div\")
      }
    }
  }
}
"
#_`[{(:scraper/g1 {:url "https://g1.globo.com"})
     [(:select/title {:xpath "tr > div > h1"})
      {(:select/comments {:xpath "tr > div > div:nth-child(5) > div"})
       [(:select/name {:xpath "h1"})
        (:select/msg {:xpath "h1"})
        {(:scraper/twitter {:params {:id "h1"}
                            :url    "https://twitter.com/{id}"})
         [(:select/last-tweet {:xpath "div > main > div"})]}]}]}]

(comment
  ;; EQL <> GraphQL equivalence:
  (pcg/query->graphql `[{(:scraper/g1 {:url "https://g1.globo.com"})
                         [(:select/title {:xpath "tr > div > h1"})]}]
    {})
  => "query {
        g1(url: \"https:\\/\\/g1.globo.com\") {
          title(xpath: \"tr > div > h1\")
        }
      }
")


(defn mock-http-client
  [{::keys [*urls ring-handler]
    :or    {*urls        (atom [])
            ring-handler (fn [req]
                           {:body   (->> [:html
                                          [:head
                                           [:title "Hello"]]
                                          [:body
                                           [:div "World"]]]
                                      (h/html {:mode :html})
                                      str)
                            :status 200})}}]
  (proxy [HttpClient] []
    (send [^HttpRequest req res]
      (swap! *urls conj (str (.uri req)))
      (let [uri (.uri req)
            {:keys [status body headers]
             :as   response} (ring-handler
                               {:request-method (keyword (string/lower-case (.method req)))
                                :server-name    (.getHost uri)
                                :uri            (str "/" (.getPath uri))
                                :server-port    (.getPort uri)
                                :scheme         (keyword (.getScheme uri))
                                :protocol       "HTTP/1.1"})
            baos (ByteArrayOutputStream.)]
        (ring.core.protocols/write-body-to-stream body response baos)
        (reify HttpResponse
          (body [this]
            (io/input-stream (.toByteArray baos)))
          (headers [this] (HttpHeaders/of (into {}
                                            (map (fn [[k v]]
                                                   (if (coll? v)
                                                     [k (into-array v)]
                                                     [k (into-array [v])])))
                                            headers)
                            (reify BiPredicate
                              (test [this a b]
                                true))))
          (statusCode [this] status))))))

(deftest with-lacinia
  (let [query "query {
                 g1: scraper(url: \"https://g1.globo.com\") {
                   title: selectString(of: \"head > title\"),
                   description: selectString(of: \"body > div\")
                 }
               }"
        variables {}
        options {}
        context {::pa/http-client (mock-http-client {})}]
    (is (-> (lacinia/execute pa/schema query variables context options)
          (doto pprint/pprint)
          (= {:data {:g1 {:title       "Hello"
                          :description "\nWorld"}}})))))



(deftest join!
  (let [query "query {
                 g1: scraper(url: \"https://g1.globo.com\") {
                   title: selectString(of: \"head > title\"),
                   description: selectString(of: \"body > div\")
                   twitter: scraper(url: \"https://twitter.com/%s\", selectParams: [\"head > title\"]) {
                     lastTweet: selectString(of: \"div > main > div\")
                   }
                 }
               }"
        variables {}
        options {}
        *urls (atom [])
        context {::pa/http-client (mock-http-client {::*urls        *urls
                                                     ::ring-handler (fn [{:keys [server-name]}]
                                                                      (let [html (case server-name
                                                                                   "g1.globo.com" [:html
                                                                                                   [:head
                                                                                                    [:title "Hello"]]
                                                                                                   [:body
                                                                                                    [:div "world"]]]

                                                                                   "twitter.com" [:html
                                                                                                  [:head
                                                                                                   [:title "tt"]]
                                                                                                  [:body
                                                                                                   [:div
                                                                                                    [:main
                                                                                                     [:div "hello-from-tt"]]]]])]
                                                                        {:body    (->> html
                                                                                    (h/html {:mode :html})
                                                                                    str)
                                                                         :headers {}
                                                                         :status  200}))})}]
    (is (-> (lacinia/execute pa/schema query variables context options)
          (doto pprint/pprint)
          (= {:data
              {:g1
               {:title       "Hello",
                :description "\nworld",
                :twitter     {:lastTweet "\nhello-from-tt"}}}})))
    (is (= ["https://g1.globo.com" "https://twitter.com/Hello"]
          @*urls))))



(deftest v1
  (let [query "
 {
   hackernews: scraper(url: \"https://news.ycombinator.com/\") {
     url
     news: selectList(of:\"tr.athing\") {
       title: selectString(of:\"a\")
       link: selectString(of:\"td.title > a\", attribute:\"href\")
     }
   }
 }"
        variables {}
        options {}
        context {::pa/http-client (HttpClient/newHttpClient)}]
    (is (-> (lacinia/execute pa/schema query variables context options)
          (doto pprint/pprint)
          (= {:data {:g1 {:title       "Hello"
                          :description "\nWorld"}}})))))
