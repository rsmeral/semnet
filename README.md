SemNet
======

_This project is not maintained._

---

A modular and extensible framework for building domain-specific semantic networks. The main purpose is automated collection of unstructured or semi-structured data from web resources and their transformation into a machine readable representation &ndash; a semantic network.

_SemNet_ is the name for a group of processors for the `PipedObjectProcessor`, the underlying data processing framework. These processors were designed to serve the purpose of SemNet – crawling the web (`HTMLCrawler`), extracting information (scrapers), optionally mapping terms between vocabularies (`StatementMapper`) and persisting the information (`SesameWriter`).

## Piped Object Processor

Piped object processor (POP) is the name given to the lowest layer of the implementation. It is a construct inspired by the design pattern called _Chain of Responsibility_. The POP is based on the notion of processing chains where information flows from the input to the output, passing through arbitrary number of object processors, each of which might perform some transformation on the received information or emit new pieces of information based on those received. Only discrete pieces of information are exchanged, not continuous data streams. Information is encapsulated in _containers_ called simply objects, since POP is based on Java, where the top-level element in type hierarchy is Object. Any Java class may serve as a _container_.

## ArtNet

ArtNet is a semantic network of works of art created using SemNet. It contains data collected from [ČSFD.cz](http://www.csfd.cz) and [DatabazeKnih.cz](http://www.databazeknih.cz) during may 2011, in the extent of

* 244 000 movies,
* 58 000 actors/directors,
* 73 000 books,
* 23 000 literary authors,

and millions of relationships. Entities in ArtNet are instances of WordNet "classes" (synsets).

---

_Created in 2011 as a bachelor's thesis at FI MUNI._