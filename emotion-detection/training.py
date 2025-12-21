import pandas as pd
from sklearn.ensemble import RandomForestClassifier, GradientBoostingClassifier
from sklearn.pipeline import Pipeline
from sklearn.svm import SVC
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import classification_report
from sklearn.preprocessing import StandardScaler
from sklearn.model_selection import train_test_split, cross_val_score
import pickle
import numpy as np



df = pd.read_csv('data.csv')

print(df.head())
print(df.columns)
print(df.info())

print(df.describe())

df = df.dropna()

features = df.drop('Class',axis=1)
labels = df['Class']


print(features)
print(labels)

# # Split the data into training and test sets
X_train, X_test, y_train, y_test = train_test_split(features, labels, test_size=0.2, random_state=42, stratify=labels)

print(f"\nTraining on {len(X_train)} samples, testing on {len(X_test)} samples...")

# Try Gradient Boosting which often performs better for tabular data
print("Training Gradient Boosting model... (this may take a few minutes)")

pipeline = Pipeline([
    ('scaler', StandardScaler()),
    ('gb', GradientBoostingClassifier(
        n_estimators=200,
        max_depth=5,
        learning_rate=0.1,
        subsample=0.8,
        random_state=42,
        verbose=1
    ))
])

print("Training model...")

# Fit the pipeline on the training data
pipeline.fit(X_train, y_train)

print("Training complete! Evaluating...")

# Predict on the test data
predictions = pipeline.predict(X_test)

# Evaluate the model
yhat = pipeline.predict(X_test)
model_performance = classification_report(y_test,yhat)
print(f"\nModel Report:\n{model_performance}")

# Calculate accuracy
from sklearn.metrics import accuracy_score
accuracy = accuracy_score(y_test, yhat)
print(f"\nOverall Accuracy: {accuracy:.2%}")

model_name = 'model.pkl'
with open(model_name,'wb')as f:
    pickle.dump(pipeline,f)

print(f"\n✓ Model saved to {model_name}")

