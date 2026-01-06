create or replace FUNCTION dime_que_carrera_returnvalue 
(sentence IN VARCHAR2) 
RETURN VARCHAR2
AS 
  question_vector vector;
  result_text VARCHAR2(4000) := '';
BEGIN 
   select dbms_vector.utl_to_embedding(sentence,
   json('
   {"provider": "ocigenai",
   "credential_name": "ocicredentialwcomp",
   "url": "https://inference.generativeai.eu-frankfurt-1.oci.oraclecloud.com/20231130/actions/embedText",
   "model": "cohere.embed-multilingual-v3.0"}
   ')
   ) 
   into question_vector;
   
   result_text := sentence || CHR(10) || CHR(10);
   
   FOR t IN 
   (
   select id_grado, nombre_grado, universidad, tipoestudio
   from grados_universidad
   order by vector_distance(vectortexto, question_vector, COSINE)
   FETCH FIRST 3 ROWS ONLY
   ) 
   LOOP 
      result_text := result_text || to_char(t.id_grado) || '  ' || t.nombre_grado || '  ' || t.universidad || CHR(10);
   END LOOP;
   
   RETURN result_text;
END;
/
