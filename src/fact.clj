(ns fact
  (:import java.util.Collection)
  (:import java.util.Map)
  (:import clojure.lang.IFn)
  (:import java.util.regex.Pattern)
  (:import java.io.FileNotFoundException))

;; Create a fact

(defstruct fact-info
  :doc
  :test
  :pending?
  :params
  :data)

(defmacro fact
  "Define a documented fact that can be verified via a series of test values
  applied to a test expression. If the expression evaluates to true for all
  values, the fact is verified.

  e.g. (fact \"The length of a list equals the sum of the length of its parts\"
         [x (rand-seqs rand-ints)
          y (rand-seqs rand-ints)]
         (= (count (concat x y))
            (+ (count x) (count y))))"
  ([doc]
   `(fact ~doc nil))
  ([doc data-map & expr]
    (let [pairs  (partition 2 data-map)
          params (map first pairs)
          data   (map second pairs)]
     `(def ~(gensym "fact")
        (struct-map fact-info
          :doc      ~doc
          :test     (fn [~@params] ~@expr)
          :pending? ~(nil? data-map)
          :params  '~(vec params)
          :data     ~(vec data))))))

;; Generate sequences of test data

(defmacro when-require
  "Execute a block of code only if the supplied namespace can be loaded."
  [ns & body]
  `(do
     (try (require ~ns)
          (catch FileNotFoundException e#))
     (when (find-ns ~ns)
       (eval '(do ~@body)))))

(derive java.util.Map ::collection)
(derive java.util.Collection ::collection)

(defmulti
  #^{:doc "Return a sequence of test values from a particular generator."}
  test-seq class)

(defmethod test-seq ::collection
  [coll]
  (seq coll))

(defmethod test-seq IFn
  [func]
  (repeatedly func))

(when-require 'rend
  (defmethod test-seq Pattern
    [re]
    (repeatedly #(rend/rend re))))

;; Verify a fact by running tests

(def #^{:doc "The maximum amount of test values to use per fact."}
  *max-amount* 50)

(defn- make-test-cases
  "Make a sequence of test cases from a number of test value sequences. The
  number of test cases is limited to *max-amount*. If the values sequences
  are of uneven length, the sequences are repeated up to the length of the
  largest value sequence."
  [vals]
  (if (seq vals)
    (let [bounded-vals   (map #(take *max-amount* (test-seq %)) vals)
          max-count      (apply max (map count bounded-vals))
          same-size-vals (map #(take max-count (cycle %)) bounded-vals)]
      (apply map vector same-size-vals))
    [[]]))

(defn- run-tests
  "Run a function with a collection of test-cases and return the results."
  [func test-cases]
  (for [vals test-cases]
    (try
      (if (apply func vals)
        [:success vals]
        [:failure vals])
    (catch Exception e
      [:exception [e vals]])
    (catch Error e
      [:exception [e vals]]))))

(defn- filter-category
  "Filter a sequence of results matching the supplied category."
  [category results]
  (map second
    (filter #(= category (first %)) results)))

(defstruct result
  :fact
  :successes
  :failures
  :exceptions)

(defn verify
  "Verify a single fact."
  [fact]
  (if (fact :pending?)
    (struct result fact nil nil nil)
    (let [results (run-tests
                    (fact :test)
                    (make-test-cases (fact :data)))]
      (struct-map result
        :fact       fact
        :successes  (filter-category :success   results)
        :failures   (filter-category :failure   results)
        :exceptions (filter-category :exception results)))))

;; Verify all facts in a namespace

(defn- fact?
  "Is a symbol a fact?"
  [sym]
  (.startsWith (name sym) "fact"))

(defn- get-facts
  "Get all the functions beginning with 'fact' from a namespace."
  [ns]
  (for [[sym var] (ns-publics ns) :when (fact? sym)]
    (var-get var)))

(defn verify-facts
  "Get a lazy list of results from all the facts in a namespace."
  ([]   (verify-facts *ns*))
  ([ns] (map verify (get-facts ns))))
