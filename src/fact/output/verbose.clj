;; Copyright (c) James Reeves. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which
;; can be found in the file epl-v10.html at the root of this distribution. By
;; using this software in any fashion, you are agreeing to be bound by the
;; terms of this license. You must not remove this notice, or any other, from
;; this software.

;; fact.output.verbose:
;; 
;; Verbose output of fact results.

(ns fact.output.verbose
  (:use fact.core))

(defn- format-params
  "Format a collection of parameters and values to a string."
  [params values]
  (apply str
    (interpose ", "
      (map #(str %1 " = " (pr-str %2))
           params
           values))))

(defn- format-exception
  "Format an exception thrown by a test as a string."
  [result]
  (let [[exception values] (first (result :exceptions))]
    (str "\n  EXCEPTION WHEN: "
      (format-params ((result :fact) :params) values)
      "\n  => " exception)))

(defn- format-failure
  "Format a failed result as a string."
  [result]
  (str "\n  FAILURE WHEN: "
    (format-params ((result :fact) :params)
                   (first (result :failures)))))

(defn format-result
  "Format a single result from a verified fact."
  [result]
  (str "- " (:doc (result :fact))
            (cond
              (pending? result)   " (pending)"
              (exception? result) (format-exception result)
              (failure? result)   (format-failure result))))

(defn print-summary
  "Print a summary of how many facts succeeded, failed, or excepted."
  [results]
  (println
    (count results) "facts,"
    (count (filter pending? results)) "pending,"
    (count (filter failure? results)) "failed,"
    (count (filter exception? results)) "exceptions")
  (println))

(defn print-results
  "Prints the results from a set of verified facts to *test-out*."
  [title results]
  (println title)
  (doseq [result results]
    (println (format-result result)))
  (print-summary results))
