-- Copyright (c) 1991-2002, The Numerical ALgorithms Group Ltd.
-- All rights reserved.
--
-- Redistribution and use in source and binary forms, with or without
-- modification, are permitted provided that the following conditions are
-- met:
--
--     - Redistributions of source code must retain the above copyright
--       notice, this list of conditions and the following disclaimer.
--
--     - Redistributions in binary form must reproduce the above copyright
--       notice, this list of conditions and the following disclaimer in
--       the documentation and/or other materials provided with the
--       distribution.
--
--     - Neither the name of The Numerical ALgorithms Group Ltd. nor the
--       names of its contributors may be used to endorse or promote products
--       derived from this software without specific prior written permission.
--
-- THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
-- IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
-- TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
-- PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
-- OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
-- EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
-- PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
-- PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
-- LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
-- NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
-- SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.


)package "BOOT"

--% Yet Another Parser Transformation File
--These functions are used by for BOOT and SPAD code
--(see new2OldLisp, e.g.)

postTransform y ==
  x:= y
  u:= postTran x
  if u is ['Tuple,:l,[":",y,t]] and (and/[IDENTP x for x in l]) then u:=
    [":",['LISTOF,:l,y],t]
  postTransformCheck u
  aplTran u

displayPreCompilationErrors() ==
  n:= #($postStack:= REMDUP NREVERSE $postStack)
  n=0 => nil
  errors:=
    1<n => '"errors"
    '"error"
  if $InteractiveMode
    then sayBrightly ['"   Semantic ",errors,'" detected: "]
    else
      heading:=
        $topOp ^= '$topOp => ['"   ",$topOp,'" has"]
        ['"   You have"]
      sayBrightly [:heading,'%b,n,'%d,'"precompilation ",errors,'":"]
  if 1<n then
    (for x in $postStack for i in 1.. repeat sayMath ['"   ",i,'"_) ",:x])
    else sayMath ['"    ",:first $postStack]
  TERPRI()

postTran x ==
  atom x =>
    postAtom x
  op := first x
  atom op and (f:= GETL(op,'postTran)) => FUNCALL(f,x)
  op is ['elt,a,b] =>
    u:= postTran [b,:rest x]
    [postTran op,:rest u]
  op is ['Scripts,:.] =>
    postScriptsForm(op,"append"/[unTuple postTran y for y in rest x])
  op^=(y:= postOp op) => [y,:postTranList rest x]
  postForm x

postTranList x == [postTran y for y in x]

postBigFloat x ==
  [.,mant, expon] := x
  $BOOT => INT2RNUM(mant) * INT2RNUM(10) ** expon
  eltword := if $InteractiveMode then "$elt" else 'elt
  postTran [[eltword,'(Float),'float],[",",[",",mant,expon],10]]

postAdd ['add,a,:b] ==
  null b => postCapsule a
  ['add,postTran a,postCapsule first b]

checkWarning msg == postError concat('"Parsing error: ",msg)
 
checkWarningIndentation() ==
  checkWarning ['"Apparent indentation error following",:bright "add"]

postCapsule x ==
  x isnt [op,:.] => checkWarningIndentation()
  INTEGERP op or op = "==" => ['CAPSULE,postBlockItem x]
  op = ";" => ['CAPSULE,:postBlockItemList postFlatten(x,";")]
  op = "if" => ['CAPSULE,postBlockItem x]
  checkWarningIndentation()

postQUOTE x == x

postColon u ==
  u is [":",x] => [":",postTran x]
  u is [":",x,y] => [":",postTran x,:postType y]

postColonColon u ==
  -- for Lisp package calling
  -- boot syntax is package::fun but probably need to parenthesize it
  $BOOT and u is ["::",package,fun] =>
    INTERN(STRINGIMAGE fun, package)
  postForm u

postAtSign ["@",x,y] == ["@",postTran x,:postType y]

postPretend ['pretend,x,y] == ['pretend,postTran x,:postType y]

postConstruct u ==
  u is ['construct,b] =>
    a:= (b is [",",:.] => comma2Tuple b; b)
    a is ['SEGMENT,p,q] => ['construct,postTranSegment(p,q)]
    a is ['Tuple,:l] =>
      or/[x is [":",y] for x in l] => postMakeCons l
      or/[x is ['SEGMENT,:.] for x in l] => tuple2List l
      ['construct,:postTranList l]
    ['construct,postTran a]
  u

postError msg ==
  BUMPERRORCOUNT 'precompilation
  xmsg:=
    $defOp ^= '$defOp and not $InteractiveMode => [$defOp,'": ",:msg]
    msg
  $postStack:= [xmsg,:$postStack]
  nil

postMakeCons l ==
  null l => 'nil
  l is [[":",a],:l'] =>
    l' => ['append,postTran a,postMakeCons l']
    postTran a
  ['cons,postTran first l,postMakeCons rest l]

postAtom x ==
  $BOOT => x
  x=0 => '(Zero)
  x=1 => '(One)
  EQ(x,'T) => 'T_$ -- rename T in spad code to T$
  IDENTP x and GETDATABASE(x,'NILADIC) => LIST x
  x

postBlock ['Block,:l,x] ==
  ['SEQ,:postBlockItemList l,['exit,postTran x]]

postBlockItemList l == [postBlockItem x for x in l]

postBlockItem x ==
  x:= postTran x
  x is ['Tuple,:l,[":",y,t]] and (and/[IDENTP x for x in l]) =>
    [":",['LISTOF,:l,y],t]
  x

postCategory (u is ['CATEGORY,:l]) ==
  --RDJ: ugh_ please -- someone take away need for PROGN as soon as possible
  null l => u
  op :=
    $insidePostCategoryIfTrue = true => 'PROGN
    'CATEGORY
  [op,:[fn x for x in l]] where fn x ==
    $insidePostCategoryIfTrue: local := true
    postTran x

postComma u == postTuple comma2Tuple u

comma2Tuple u == ['Tuple,:postFlatten(u,",")]

postDef [defOp,lhs,rhs] ==
--+
  lhs is ["macro",name] => postMDef ["==>",name,rhs]

  if not($BOOT) then recordHeaderDocumentation nil
  if $maxSignatureLineNumber ^= 0 then
    $docList := [['constructor,:$headerDocumentation],:$docList]
    $maxSignatureLineNumber := 0
    --reset this for next constructor; see recordDocumentation
  lhs:= postTran lhs
  [form,targetType]:=
    lhs is [":",:.] => rest lhs
    [lhs,nil]
  if null $InteractiveMode and atom form then form := LIST form
  newLhs:=
    atom form => form
    [op,:argl]:= [(x is [":",a,.] => a; x) for x in form]
    [op,:postDefArgs argl]
  argTypeList:=
    atom form => nil
    [(x is [":",.,t] => t; nil) for x in rest form]
  typeList:= [targetType,:argTypeList]
  if atom form then form := [form]
  specialCaseForm := [nil for x in form]
  ['DEF,newLhs,typeList,specialCaseForm,postTran rhs]

postDefArgs argl ==
  null argl => argl
  argl is [[":",a],:b] =>
    b => postError
      ['"   Argument",:bright a,'"of indefinite length must be last"]
    atom a or a is ['QUOTE,:.] => a
    postError
      ['"   Argument",:bright a,'"of indefinite length must be a name"]
  [first argl,:postDefArgs rest argl]

postMDef(t) ==
  [.,lhs,rhs] := t
  $InteractiveMode and not $BOOT =>
    lhs := postTran lhs
    null IDENTP lhs => throwKeyedMsg("S2IP0001",NIL)
    ['MDEF,lhs,NIL,NIL,postTran rhs]
  lhs:= postTran lhs
  [form,targetType]:=
    lhs is [":",:.] => rest lhs
    [lhs,nil]
  form:=
    atom form => LIST form
    form
  newLhs:= [(x is [":",a,:.] => a; x) for x in form]
  typeList:= [targetType,:[(x is [":",.,t] => t; nil) for x in rest form]]
  ['MDEF,newLhs,typeList,[nil for x in form],postTran rhs]

postElt (u is [.,a,b]) ==
  a:= postTran a
  b is ['Sequence,:.] => [['elt,a,'makeRecord],:postTranList rest b]
  ['elt,a,postTran b]

postExit ["=>",a,b] == ['IF,postTran a,['exit,postTran b],'noBranch]


postFlatten(x,op) ==
  x is [ =op,a,b] => [:postFlatten(a,op),:postFlatten(b,op)]
  LIST x

postForm (u is [op,:argl]) ==
  x:=
    atom op =>
      argl':= postTranList argl
      [op,:argl']
    op is ['Scripts,:.] => [:postTran op,:postTranList argl]
    u:= postTranList u
    if u is [['Tuple,:.],:.] then
      postError ['"  ",:bright u,
        '"is illegal because tuples cannot be applied!",'%l,
          '"   Did you misuse infix dot?"]
    u
  x is [.,['Tuple,:y]] => [first x,:y]
  x

postQuote [.,a] == ['QUOTE,a]

postScriptsForm(['Scripts,op,a],argl) ==
  [getScriptName(op,a,#argl),:postTranScripts a,:argl]

postScripts ['Scripts,op,a] ==
  [getScriptName(op,a,0),:postTranScripts a]

getScriptName(op,a,numberOfFunctionalArgs) ==
  if null IDENTP op then
    postError ['"   ",op,'" cannot have scripts"]
  INTERNL("*",STRINGIMAGE numberOfFunctionalArgs,
    decodeScripts a,PNAME op)

postTranScripts a ==
  a is ['PrefixSC,b] => postTranScripts b
  a is [";",:b] => "append"/[postTranScripts y for y in b]
  a is [",",:b] =>
    ("append"/[fn postTran y for y in b]) where
      fn x ==
        x is ['Tuple,:y] => y
        LIST x
  LIST postTran a

decodeScripts a ==
  a is ['PrefixSC,b] => STRCONC(STRINGIMAGE 0,decodeScripts b)
  a is [";",:b] => APPLX('STRCONC,[decodeScripts x for x in b])
  a is [",",:b] =>
    STRINGIMAGE fn a where fn a == (a is [",",:b] => +/[fn x for x in b]; 1)
  STRINGIMAGE 1

postIf t ==
  t isnt ["if",:l] => t
  ['IF,:[(null (x:= postTran x) and null $BOOT => 'noBranch; x)
    for x in l]]

postJoin ['Join,a,:l] ==
  a:= postTran a
  l:= postTranList l
  if l is [b] and b is [name,:.] and MEMQ(name,'(ATTRIBUTE SIGNATURE)) then l
    := LIST ['CATEGORY,b]
  al:=
    a is ['Tuple,:c] => c
    LIST a
  ['Join,:al,:l]

postMapping u  ==
  u isnt ["->",source,target] => u
  ['Mapping,postTran target,:unTuple postTran source]

postOp x ==
  x=":=" =>
    $BOOT => 'SPADLET
    'LET
  x=":-" => 'LETD
  x='Attribute => 'ATTRIBUTE
  x

postRepeat ['REPEAT,:m,x] == ['REPEAT,:postIteratorList m,postTran x]

postSEGMENT ['SEGMENT,a,b] ==
  key:= [a,'"..",:(b => [b]; nil)]
  postError ['"   Improper placement of segment",:bright key]

postCollect [constructOp,:m,x] ==
  x is [['elt,D,'construct],:y] =>
    postCollect [['elt,D,'COLLECT],:m,['construct,:y]]
  itl:= postIteratorList m
  x:= (x is ['construct,r] => r; x)  --added 84/8/31
  y:= postTran x
  finish(constructOp,itl,y) where
    finish(op,itl,y) ==
      y is [":",a] => ['REDUCE,'append,0,[op,:itl,a]]
      y is ['Tuple,:l] =>
        newBody:=
          or/[x is [":",y] for x in l] => postMakeCons l
          or/[x is ['SEGMENT,:.] for x in l] => tuple2List l
          ['construct,:postTranList l]
        ['REDUCE,'append,0,[op,:itl,newBody]]
      [op,:itl,y]

postTupleCollect [constructOp,:m,x] ==
  postCollect [constructOp,:m,['construct,x]]

postIteratorList x ==
  x is [p,:l] =>
    (p:= postTran p) is ['IN,y,u] =>
      u is ["|",a,b] => [['IN,y,postInSeq a],["|",b],:postIteratorList l]
      [['IN,y,postInSeq u],:postIteratorList l]
    [p,:postIteratorList l]
  x

postin arg ==
  arg isnt ["in",i,seq] => systemErrorHere '"postin"
  ["in",postTran i, postInSeq seq]

postIn arg ==
  arg isnt ['IN,i,seq] => systemErrorHere '"postIn"
  ['IN,postTran i,postInSeq seq]

postInSeq seq ==
  seq is ['SEGMENT,p,q] => postTranSegment(p,q)
  seq is ['Tuple,:l] => tuple2List l
  postTran seq

postTranSegment(p,q) == ['SEGMENT,postTran p,(q => postTran q; nil)]

tuple2List l ==
  l is [a,:l'] =>
    u:= tuple2List l'
    a is ['SEGMENT,p,q] =>
      null u => ['construct,postTranSegment(p,q)]
      $InteractiveMode and null $BOOT =>
        ['append,['construct,postTranSegment(p,q)],tuple2List l']
      ["nconc",['construct,postTranSegment(p,q)],tuple2List l']
    null u => ['construct,postTran a]
    ["cons",postTran a,tuple2List l']
  nil

SEGMENT(a,b) == [i for i in a..b]

postReduce ['Reduce,op,expr] ==
  $InteractiveMode or expr is ['COLLECT,:.] =>
    ['REDUCE,op,0,postTran expr]
  postReduce ['Reduce,op,['COLLECT,['IN,g:= GENSYM(),expr],
    ['construct,  g]]]

postFlattenLeft(x,op) ==--
  x is [ =op,a,b] => [:postFlattenLeft(a,op),b]
  [x]

postSemiColon u == postBlock ['Block,:postFlattenLeft(u,";")]

postSequence ['Sequence,:l] == ['(elt $ makeRecord),:postTranList l]

postSignature ['Signature,op,sig] ==
  sig is ["->",:.] =>
    sig1:= postType sig
    op:= postAtom (STRINGP op => INTERN op; op)
    ["SIGNATURE",op,:removeSuperfluousMapping killColons postDoubleSharp sig1]

postDoubleSharp sig ==
  sig is [['Mapping,target,:r]] =>
    -- replace #1,... by ##1,...
    [['Mapping, SUBLISLIS($FormalFunctionParameterList, _
                   $FormalMapVariableList, target), _
         :r]]
  sig

killColons x ==
  atom x => x
  x is ['Record,:.] => x
  x is ['Union,:.] => x
  x is [":",.,y] => killColons y
  [killColons first x,:killColons rest x]

postSlash ['_/,a,b] ==
  STRINGP a => postTran ['Reduce,INTERN a,b]
  ['_/,postTran a,postTran b]

removeSuperfluousMapping sig1 ==
  --get rid of this asap
  sig1 is [x,:y] and x is ['Mapping,:.] => [rest x,:y]
  sig1

postType typ ==
  typ is ["->",source,target] =>
    source="constant" => [LIST postTran target,"constant"]
    LIST ['Mapping,postTran target,:unTuple postTran source]
  typ is ["->",target] => LIST ['Mapping,postTran target]
  LIST postTran typ

postTuple u ==
  u is ['Tuple] => u
  u is ['Tuple,:l,a] => (['Tuple,:postTranList rest u])
--u is ['Tuple,:l,a] => (--a:= postTran a; ['Tuple,:postTranList rest u])
    --RDJ: don't understand need for above statement that is commented out

postWhere ["where",a,b] ==
  x:=
    b is ['Block,:c] => c
    LIST b
  ["where",postTran a,:postTranList x]

postWith ["with",a] ==
  $insidePostCategoryIfTrue: local := true
  a:= postTran a
  a is [op,:.] and MEMQ(op,'(SIGNATURE ATTRIBUTE IF)) => ['CATEGORY,a]
  a is ['PROGN,:b] => ['CATEGORY,:b]
  a

postTransformCheck x ==
  $defOp: local:= nil
  postcheck x

postcheck x ==
  atom x => nil
  x is ['DEF,form,[target,:.],:.] =>
    (setDefOp form; postcheckTarget target; postcheck rest rest x)
  x is ['QUOTE,:.] => nil
  postcheck first x
  postcheck rest x

setDefOp f ==
  if f is [":",g,:.] then f := g
  f := (atom f => f; first f)
  if $topOp then $defOp:= f else $topOp:= f

postcheckTarget x ==
  -- doesn't seem that useful!
  isPackageType x => nil
  x is ['Join,:.] => nil
  NIL

isPackageType x == not CONTAINED("$",x)

unTuple x ==
  x is ['Tuple,:y] => y
  LIST x

--% APL TRANSFORMATION OF INPUT

aplTran x ==
  $BOOT => x
  $GENNO: local := 0
  u:= aplTran1 x
  containsBang u => throwKeyedMsg("S2IP0002",NIL)
  u

containsBang u ==
  atom u => EQ(u,"!")
  u is [='QUOTE,.] => false
  or/[containsBang x for x in u]

aplTran1 x ==
  atom x => x
  [op,:argl1] := x
  argl := aplTranList argl1
  -- unary case f ! y
  op = "_!" =>
    argl is [f,y] =>
      y is [op',:y'] and op' = "_!" => aplTran1 [op,op,f,:y']
      $BOOT => ['COLLECT,['IN,g:=GENVAR(),aplTran1 y],[f,g]]
      ['map,f,aplTran1 y]
    x    --do not handle yet
  -- multiple argument case
  hasAplExtension argl is [arglAssoc,:futureArgl] =>
    -- choose the last aggregate type to be result of reshape
    ['reshape,['COLLECT,:[['IN,g,['ravel,a]] for [g,:a] in arglAssoc],
      aplTran1 [op,:futureArgl]],CDAR arglAssoc]
  [op,:argl]

aplTranList x ==
  atom x => x
  [aplTran1 first x,:aplTranList rest x]

hasAplExtension argl ==
  or/[x is ["_!",:.] for x in argl] =>
    u:= [futureArg for x in argl] where futureArg ==
      x is ["_!",y] =>
        z:= deepestExpression y
        arglAssoc := [[g := GENVAR(),:aplTran1 z],:arglAssoc]
        substitute(g,z,y)
      x
    [arglAssoc,:u]
  nil

deepestExpression x ==
  x is ["_!",y] => deepestExpression y
  x