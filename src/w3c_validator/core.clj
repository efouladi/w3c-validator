(ns w3c-validator.core
  (:gen-class)
  (:require [net.cgrand.enlive-html :as h]
            [clojure.zip :as z]
            [clojure.xml :as xml]
            [clj-http.client :as client])
  (:import (java.net URL MalformedURLException)) 
  (:import java.io.FileNotFoundException) 
  )

;This atom is used to the keep the list of visited urls
(def visited (atom #{}))

(def validator-url {:html "http://validator.w3.org/check?output=soap12&url="
                    :css "http://jigsaw.w3.org/css-validator/validator?output=soap12&uri="})

(defn get-absolute-url-same-host
  "Convert the URL to absolute form if it's already not. Returns nil if the url is not from the same host"
  [url parent]
  (try (let [u (URL. url)]
         (when (= (.getHost u) (.getHost parent))
           (.toString u)))
    (catch MalformedURLException e (.toString (URL. parent url)))
    ))



(defn get-links 
  "Return all the links in a URI"
  [url]
    (if-not (nil? url)
      (try 
        (let [j-url (java.net.URL. url)
              page (h/html-resource j-url)]
          (swap! visited conj url) 
          (seq (map #(get-absolute-url-same-host (:href (:attrs %)) j-url) (h/select page [(h/attr? :href)]))))
      ;TODO: handle situations with files other than html such as images.. correctly
        (catch Exception e (println "invalid URL: " url)))))


(defn cont [url]
  (not (contains? @visited url)))

(defn validate-url 
  "Use w3c validator service to validate the content of the url
  Returns a tree of xml/elemet struct-map created from the output of
  validator service"
  [url]
  ;It is advised by the w3 validator service to wait at least 1 second between each request
  (Thread/sleep 1000)
  (let [mime (subs url (inc (.lastIndexOf url ".")))] 
    (xml/parse (java.io.ByteArrayInputStream.
               (.getBytes (:body (client/get (str (validator-url (keyword mime) (validator-url :html))  url))))))))


 
(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  ;; work around dangerous default behaviour in Clojure
  (alter-var-root #'*read-eval* (constantly false))
  (reset! visited #{})
  (dorun 
    (tree-seq cont get-links (first args)))
  (pprint @visited)
  (map #(:content (first (h/select (validate-url %) [:m:validity]))) @visited)
  
  )
