(ns yhnam.jetty-wrapper
  (:require [yhnam.protocols :as protocols]
            [cheshire.core :as json]
            [clojure.java.io :as io])
  (:import [org.eclipse.jetty.util.thread QueuedThreadPool]
           [org.eclipse.jetty.server Server Request ServerConnector]
           [org.eclipse.jetty.server.handler AbstractHandler]
           [javax.servlet.http HttpServletRequest HttpServletResponse]
           [java.util Locale]))

(defn build-request-map
  "Create the request map from the HttpServletRequest object."
  [^HttpServletRequest request]
  {:server-port        (.getServerPort request)
   :server-name        (.getServerName request)
   :remote-addr        (.getRemoteAddr request)
   :uri                (.getRequestURI request)
   :query-string       (.getQueryString request)
   :scheme             (keyword (.getScheme request))
   :request-method     (keyword (.toLowerCase (.getMethod request) Locale/ENGLISH))
   :protocol           (.getProtocol request)
;   :headers            (get-headers request)
   :content-type       (.getContentType request)
   ;:content-length     (get-content-length request)
   :character-encoding (.getCharacterEncoding request)

   :body               (.getInputStream request)})

(defn make-output-stream
  [^HttpServletResponse response]
  (.getOutputStream response))

(defn set-headers [^HttpServletResponse response headers]
  (doseq [[key val-or-vals] headers]
    (if (string? val-or-vals)
      (.setHeader response key val-or-vals)
      (doseq [val val-or-vals]
        (.addHeader response key val))))
  (when-let [content-type (get headers "Content-Type")]
    (.setContentType response content-type)))

(defn update-servlet-response [response response-map]
  (let [output-stream (make-output-stream response)]
    (set-headers response (:headers response-map))
    (protocols/write-body-to-stream (:body response-map) response output-stream)))

(defn ^AbstractHandler proxy-handler [handler]
  (proxy [AbstractHandler] []
    (handle [this ^Request base-request ^HttpServletRequest req ^HttpServletResponse response]
      (let [req-map (build-request-map req)
            response-map (handler req-map)]
        (update-servlet-response response response-map)
        (.setHandled base-request true)
        ))))

(defn http-connector [server options]
  (let [sc (ServerConnector. server)]
      (.setPort sc (options :port 3000))
      sc))

(defn thread-pool []
  (QueuedThreadPool.))

(defn create-server [opts]
  (let [server (Server. (thread-pool))]
    (.addConnector server (http-connector server opts))
    server))

(defn run-jetty
  [handler opts]
  (let [server (create-server opts)]
    (.setHandler server (proxy-handler handler))
    (.start server)
    server))

(defn- hello-world [request]
  {:status  200
   :headers {"Content-Type" "text/plain"}
   :body    "Hello World"})

(defn print-request-map-middleware [handler]
  (fn [req]
    (println "middleware" req)
    (handler req)))

(defn hello-world-json [request]
  {:status 200
   :headers {"Content-Type" "application/json; charset=utf-8"}
   :body {:hello "world"}})


;; JsonStreamingResponseBody를 생성하고, StreamableResponseBody를 확장한다.
;; 확장하면서 write-body-to-stream을 지정해야 한다.
;; 실제로 하는 일은 (json/generate-stream body (io/write output-stream))
;; 이 함수는 update-servlet-response에서 실행한다.
;; 단순히 stream을 쓰지 않고 string으로 바꿔도 동작하는 이유가 바로
;; write-body-to-stream에 String도 구현했기 때문이다.
(defrecord JsonStreamingResponseBody [body]
  protocols/StreamableResponseBody
  (protocols/write-body-to-stream [this _ output-stream]
    (println this)
    (json/generate-stream body (io/writer output-stream) )))

(defn json-response
  "JSON response로 map, vector를 변경한다."
  [response]
  (let [json-resp (update-in response
                             [:body]
                             #_->JsonStreamingResponseBody  ; stream으로 리턴.
                             json/generate-string       ; string으로 리턴.
                             )]
    (assoc-in json-resp [:headers "Content-Type"] "application/json; charset=utf-8")))

(defn wrap-json-response [handler]
  (fn [req]
    (json-response (handler req))))

(comment
  (def running-server (run-jetty (-> hello-world
                                     print-request-map-middleware)
                                 {}))
  (.stop running-server)

  (def running-json-server (run-jetty (-> hello-world-json
                                          wrap-json-response)
                                      {}))
  (.stop running-json-server)

  ;;
  )
