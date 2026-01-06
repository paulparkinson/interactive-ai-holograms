create or replace PROCEDURE dime_que_carrera 
(sentence IN VARCHAR2) 
AS 
  question_vector vector;
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
   dbms_output.put_line(to_char(sentence));
   dbms_output.put_line('');
   FOR t IN 
   (
   select id_grado, nombre_grado, universidad, tipoestudio
   from grados_universidad
   order by vector_distance(vectortexto, question_vector, COSINE)
   FETCH FIRST 3 ROWS ONLY
   ) 
   LOOP 
      dbms_output.put_line(to_char(t.id_grado) || '  ' || t.nombre_grado || '  ' || t.universidad  ); 
   END LOOP; 
END;
/
