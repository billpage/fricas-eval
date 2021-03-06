\documentclass{article}
\usepackage{axiom}
\begin{document}
\title{\$SPAD/src/algebra invutils.as}
\author{The Axiom Team}
\maketitle
\begin{abstract}
\end{abstract}
\eject
\tableofcontents
\eject
\section{IVUtilities}
<<IVUtilities>>=
#include "axiom"

IVUtilities: with {
	walkTree: ((IVNodeObject, Boolean) -> Boolean,
		   (IVNodeObject, Boolean) -> Boolean,
		   IVNodeObject, Boolean) -> ();
	write!:   (TextFile, IVNodeObject) -> ();
	write!:   (FileName, IVNodeObject) -> ();
} == add {
	-- walk a tree from 'root', and call f on each node.
	-- nodesOnly will stop the recursion finding subnodes within
	-- fields.
	-- preFn is a function that takes a node, a flag indicating if the
	-- node has already been traversed.  It returns a flag
 	-- indicating if the traversal should descend the node.
	walkTree(preFn:  (IVNodeObject, Boolean) -> Boolean,
		 postFn: (IVNodeObject, Boolean) -> Boolean,
		 root: 	    IVNodeObject,
		 nodesOnly: Boolean): () == {
		tab: Table(Integer, IVNodeObject) := table();
		innerWalk(node: IVNodeObject): () == {
			import from List IVNodeObject;
			import from List IVField;
			import from IVValue;
			present := key?(uniqueID node, tab);
			not preFn(node, present) => return;
			tab.(uniqueID node) := node;
			for child in children node repeat
				innerWalk(child);
			if not nodesOnly then {
				for fld in fields node repeat {
					import from IVNodeConnection;
					connect? value fld =>
						innerWalk(node coerce value fld);
					node? value fld =>
						innerWalk(coerce value fld);
				}
			}
			postFn(node, false);
		}
		innerWalk(root);
	}

	write!(out: TextFile, root: IVNodeObject): () == {
		import from Boolean;
		names: Table(Integer, IVNodeObject) := table();

		getName(node: IVNodeObject): String == {
			import from Integer;
		convert uniqueID node;
		}

		doNamingVisit(node: IVNodeObject, flag: Boolean): Boolean == {
			if flag then names.(uniqueID node) := node;
			flag
		}
		writeNodeHeader(node: IVNodeObject): () == {
			present := key?(uniqueID node, names);
			if present then {
				write!(out, "DEF ");
				write!(out, getName node);
			}
		}
		doPrintingVisit(node: IVNodeObject,
				flag: Boolean): Boolean == {
			if flag then {
				write!(out, "USE ");
				write!(out, getName node);
				return false;
			}
			write!(out, className(node));
			writeLine!(out, " {");
			import from List IVField, Symbol;
			for field in fields node repeat {
				import from IVNodeConnection;
				val: IVValue := value field;
				write!(out, string name field);
				write!(out, " ");
				node? val => {
					walkTree(doPrintingVisit,
						 doFinalPrint,
						 coerce val, false);
				}
				connect? val => {
					walkTree(doPrintingVisit,
						 doFinalPrint,
						 node coerce val, false);
					write!(out, ".");
					writeLine!(out,
						   string field coerce val);
				}
				-- simple case:
				invWrite(out, value field);
			}
			return true;
		}

		doFinalPrint(node: IVNodeObject, x: Boolean): Boolean == {
			writeLine!(out, "}");
			true;
		}
		doNothing(node: IVNodeObject, x: Boolean): Boolean == x;

		writeLine!(out, "#Inventor V2.0 ascii");
		walkTree(doNamingVisit, doNothing, root, true);
		walkTree(doPrintingVisit, doFinalPrint, root, false);
	}

	write!(file: FileName, root:IVNodeObject): () == {
		out: TextFile := open(file, "output");
		write!(out, root);
		close!(out);
	}
}

@
\section{License}
<<license>>=
--Copyright (c) 1991-2002, The Numerical ALgorithms Group Ltd.
--All rights reserved.
--
--Redistribution and use in source and binary forms, with or without
--modification, are permitted provided that the following conditions are
--met:
--
--    - Redistributions of source code must retain the above copyright
--      notice, this list of conditions and the following disclaimer.
--
--    - Redistributions in binary form must reproduce the above copyright
--      notice, this list of conditions and the following disclaimer in
--      the documentation and/or other materials provided with the
--      distribution.
--
--    - Neither the name of The Numerical ALgorithms Group Ltd. nor the
--      names of its contributors may be used to endorse or promote products
--      derived from this software without specific prior written permission.
--
--THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
--IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
--TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
--PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
--OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
--EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
--PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
--PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
--LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
--NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
--SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
@
<<*>>=
<<license>>

<<IVUtilities>>
@
\eject
\begin{thebibliography}{99}
\bibitem{1} nothing
\end{thebibliography}
\end{document}
