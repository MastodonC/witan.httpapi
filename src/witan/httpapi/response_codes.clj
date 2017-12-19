(ns witan.httpapi.response-codes)

(def OK 200)
(def CREATED 201)
(def ACCEPTED 202)
(def REDIRECT 302)
(def SEE_OTHER 303)
(def BAD_REQUEST 400)
(def UNAUTHORISED 401)
(def NOT_FOUND 404)

(defn code->reason
  [c]
  (case c
    OK           "OK"
    CREATED      "Created"
    ACCEPTED     "Accepted"
    REDIRECT     "Redirected"
    SEE_OTHER    "See Other"
    BAD_REQUEST  "Bad Request"
    UNAUTHORISED "Unauthorised"
    NOT_FOUND    "Not Found"))
